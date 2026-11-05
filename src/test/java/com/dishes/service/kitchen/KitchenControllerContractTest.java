package com.dishes.service.kitchen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.dishes.api.KitchenAdminApiController;
import com.dishes.api.KitchenPublicApiController;
import com.dishes.domain.DishesStore;
import com.dishes.service.publics.PublicAccessService;
import com.dishes.service.publics.PublicDomainWhitelistService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.user.service.UserService;

class KitchenControllerContractTest {
    private final KitchenRepository repository = mock(KitchenRepository.class);
    private final DishesStore catalogue = mock(DishesStore.class);
    private final UserService users = mock(UserService.class);
    private final KitchenIdentityService identities = new KitchenIdentityService(users);
    private final KitchenService kitchen = new KitchenService(repository, catalogue,
        Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC));
    private final KitchenPublicApiController publicController = new KitchenPublicApiController(kitchen, identities,
        mock(PublicAccessService.class), mock(PublicDomainWhitelistService.class));
    private final KitchenAdminApiController adminController = new KitchenAdminApiController(kitchen, identities);

    @BeforeEach
    void emptyKitchen() {
        when(repository.load(any(), any())).thenAnswer(ignored -> new KitchenState(null, false, new ArrayList<>(), null));
        when(catalogue.listDishes(any())).thenReturn(List.of());
    }

    @Test
    void guestRoutesResolveNamedQueryParametersAndIssueHttpOnlyCookie() {
        var client = WebTestClient.bindToController(publicController, adminController).build();
        client.get().uri("/plugins/dishes/public/viewer").exchange()
            .expectStatus().isOk().expectCookie().httpOnly(KitchenIdentityService.COOKIE_NAME, true)
            .expectBody().jsonPath("$.data.authenticated").isEqualTo(false)
            .jsonPath("$.data.canManage").isEqualTo(false);
        client.get().uri("/plugins/dishes/public/order?date=2026-11-05&period=lunch").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.data.period").isEqualTo("lunch")
            .jsonPath("$.data.mine.revision").isEqualTo(0);
        client.get().uri("/plugins/dishes/public/daily-menu?date=2026-11-05").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.data.periods").isEmpty();
        client.get().uri("/apis/plugins/dishes/admin/kitchen?date=2026-11-05").exchange()
            .expectStatus().isForbidden().expectBody().jsonPath("$.code").isEqualTo("DISHES_CHEF_REQUIRED");
        client.post().uri("/apis/plugins/dishes/admin/kitchen/commit").bodyValue(Map.of(
                "date", "2026-11-05", "period", "lunch", "revision", 0,
                "stateRevision", 0, "batchId", "initial", "mode", "menu",
                "contributionVersions", Map.of(), "items", List.of(), "note", ""))
            .exchange().expectStatus().isForbidden();
        verify(repository, never()).saveState(any());
    }

    @Test
    void chefPrincipalFromExchangeIsResolvedServerSideWithoutGlobalSecurityContext() {
        var user = new User(); var spec = new User.UserSpec(); spec.setDisplayName("真实主厨"); user.setSpec(spec);
        when(users.getUser("chef-private-account")).thenReturn(Mono.just(user));
        var principal = new UsernamePasswordAuthenticationToken("chef-private-account", "",
            List.of(new SimpleGrantedAuthority("ROLE_super-role")));
        var client = WebTestClient.bindToController(publicController, adminController)
            .webFilter((exchange, chain) -> chain.filter(exchange.mutate().principal(Mono.just(principal)).build())).build();
        client.get().uri("/plugins/dishes/public/viewer").exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.data.displayName").isEqualTo("真实主厨")
            .jsonPath("$.data.canManage").isEqualTo(true)
            .jsonPath("$.data.username").doesNotExist();
        client.get().uri("/apis/plugins/dishes/admin/kitchen?date=2026-11-05").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.data.periods.length()").isEqualTo(3);
        client.post().uri("/apis/plugins/dishes/admin/kitchen/commit").bodyValue(Map.of(
                "date", "2026-11-05", "period", "lunch", "revision", 0, "stateRevision", 0,
                "batchId", "initial", "contributionVersions", Map.of(), "mode", "menu", "items", List.of(), "note", ""))
            .exchange().expectStatus().isOk().expectBody().jsonPath("$.ok").isEqualTo(true);
        verify(repository).saveState(any());
    }
}
