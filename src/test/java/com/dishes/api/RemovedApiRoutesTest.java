package com.dishes.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dishes.extension.DishesSettings;
import com.dishes.service.AdminFacadeService;
import com.dishes.service.PublicFacadeService;
import com.dishes.service.dto.ApiPayloads;
import com.dishes.service.publics.PublicDomainWhitelistService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class RemovedApiRoutesTest {
    private final AdminFacadeService admin = mock(AdminFacadeService.class);
    private final PublicFacadeService publicApi = mock(PublicFacadeService.class);
    private final PublicDomainWhitelistService domains = mock(PublicDomainWhitelistService.class);
    private final WebTestClient client = WebTestClient.bindToController(
        new AdminApiController(admin), new PublicApiController(publicApi, domains)).build();

    @Test
    void removedOrderBackupAndRecommendationGetRoutesAreNotRegistered() {
        for (var path : List.of(
            "/apis/plugins/dishes/admin/orders",
            "/apis/plugins/dishes/admin/orders/1",
            "/apis/plugins/dishes/admin/backup/export",
            "/plugins/dishes/public/meal-orders/today",
            "/plugins/dishes/public/meal-orders/history?from=2026-11-05&to=2026-11-06",
            "/plugins/dishes/public/recommendations/random-by-period?code=lunch"
        )) client.get().uri(path).exchange().expectStatus().isNotFound();
        verifyNoInteractions(admin, publicApi);
    }

    @Test
    void removedOrderAndBackupPostRoutesAreNotRegistered() {
        for (var path : List.of("/plugins/dishes/public/meal-orders", "/apis/plugins/dishes/admin/backup/import"))
            client.post().uri(path).bodyValue(Map.of()).exchange().expectStatus().isNotFound();
        verifyNoInteractions(admin, publicApi);
    }

    @Test
    void currentCatalogueAccessAndCsrfRoutesRemainAvailable() {
        when(publicApi.dishes(eq(7L), isNull())).thenReturn(Map.of("items", List.of(), "tags", List.of()));
        when(publicApi.accessStatus(null)).thenReturn(Map.of("access_mode", "none", "granted", true));
        client.get().uri("/plugins/dishes/public/dishes?categoryId=7").exchange().expectStatus().isOk();
        client.get().uri("/plugins/dishes/public/access/status").exchange().expectStatus().isOk();
        client.get().uri("/plugins/dishes/public/csrf-metadata").exchange().expectStatus().isOk();
        verify(publicApi).dishes(7L, null);
        verify(domains, times(3)).ensureAllowed(any());
    }

    @Test
    void currentSettingsExposeOnlyBasicConfigurationAndSaveCurrentFields() {
        when(admin.getSettings()).thenReturn(ApiPayloads.settingsPayload(new DishesSettings.Spec(), "none"));
        when(admin.updateSettings(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of("updated", true));
        client.get().uri("/apis/plugins/dishes/admin/settings").exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.data.basic.accessMode").isEqualTo("none").jsonPath("$.data.notify").doesNotExist();
        client.put().uri("/apis/plugins/dishes/admin/settings").bodyValue(Map.of("basic", Map.of(
            "accessMode", "password", "accessPassword", "secret", "publicAccessUrl", "/dishes",
            "publicLogoUrl", "/lemon.svg", "publicSiteTitle", "私厨", "publicBrandTitle", "柠檬私厨",
            "publicBrandSubtitle", "今天想吃什么", "publicDomainWhitelist", "blog.lemonslice.cloud"
        ))).exchange().expectStatus().isOk();
        verify(admin).updateSettings("password", "secret", "/dishes", "/lemon.svg", "私厨", "柠檬私厨", "今天想吃什么", "blog.lemonslice.cloud");
    }
}
