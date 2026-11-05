package com.dishes.service.kitchen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.user.service.UserService;

class KitchenIdentityServiceTest {
    private final UserService users = mock(UserService.class);
    private final KitchenIdentityService identities = new KitchenIdentityService(users);

    @Test
    void guestCookieIsRandomHttpOnlyAndHttpsSecureAndOwnershipIsNotNickname() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("https://blog.example/dishes"));
        var identity = identities.resolve(null, exchange);
        var cookie = exchange.getResponse().getCookies().getFirst(KitchenIdentityService.COOKIE_NAME);
        assertNotNull(cookie);
        assertTrue(cookie.isHttpOnly()); assertTrue(cookie.isSecure());
        assertEquals("Lax", cookie.getSameSite()); assertEquals("/", cookie.getPath());
        assertEquals(32, java.util.Base64.getUrlDecoder().decode(cookie.getValue()).length);
        assertTrue(identity.guest()); assertFalse(identity.canManage());
        assertEquals("", identity.avatarUrl());
        assertNotEquals(cookie.getValue(), identity.ownerKey());
        var again = MockServerWebExchange.from(MockServerHttpRequest.get("http://127.0.0.1:8092/dishes")
            .cookie(new HttpCookie(KitchenIdentityService.COOKIE_NAME, cookie.getValue())));
        assertEquals(identity.ownerKey(), identities.resolve(null, again).ownerKey());
        var other = MockServerWebExchange.from(MockServerHttpRequest.get("http://127.0.0.1:8092/dishes"));
        assertNotEquals(identity.ownerKey(), identities.resolve(null, other).ownerKey());
        assertFalse(other.getResponse().getCookies().getFirst(KitchenIdentityService.COOKIE_NAME).isSecure());
        verifyNoInteractions(users);
    }

    @Test
    void authenticatedIdentityUsesServerUserAndSuperRoleWhileAnonymousTokenStaysGuest() {
        var user = new User(); var spec = new User.UserSpec(); spec.setDisplayName("服务端昵称");
        spec.setAvatar(" https://img.example/avatar.png "); spec.setEmail("private@example.invalid"); spec.setPassword("private-hash"); user.setSpec(spec);
        when(users.getUser("account-name")).thenReturn(Mono.just(user));
        var auth = new UsernamePasswordAuthenticationToken("account-name", "", List.of(new SimpleGrantedAuthority("ROLE_super-role")));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("http://localhost/dishes"));
        var identity = identities.resolve(auth, exchange);
        assertEquals("account-name", identity.username()); assertEquals("服务端昵称", identity.displayName());
        assertEquals("https://img.example/avatar.png", identity.avatarUrl());
        assertFalse(identity.guest()); assertTrue(identity.canManage());
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_anonymous")));
        assertTrue(anonymous.isAuthenticated());
        assertTrue(identities.resolve(anonymous, MockServerWebExchange.from(MockServerHttpRequest.get("http://localhost/dishes"))).guest());
        verify(users, times(1)).getUser("account-name");
        verifyNoMoreInteractions(users);
    }
}
