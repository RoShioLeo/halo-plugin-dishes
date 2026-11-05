package com.dishes.service.kitchen;

import com.dishes.api.BusinessErrorCode;
import com.dishes.api.BusinessException;
import com.dishes.service.kitchen.KitchenModels.Identity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import run.halo.app.core.user.service.UserService;

@Service
public class KitchenIdentityService {
    public static final String COOKIE_NAME = "ls_dishes_guest";
    private final SecureRandom random = new SecureRandom();
    private final AuthenticationTrustResolverImpl trust = new AuthenticationTrustResolverImpl();
    private final UserService users;

    public KitchenIdentityService(UserService users) { this.users = users; }

    public Identity resolve(Principal principal, ServerWebExchange exchange) {
        var cookie = exchange.getRequest().getCookies().getFirst(COOKIE_NAME);
        var secret = cookie == null ? "" : cookie.getValue();
        if (!secret.matches("[A-Za-z0-9_-]{43}")) {
            byte[] bytes = new byte[32]; random.nextBytes(bytes);
            secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            var secure = "https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme());
            exchange.getResponse().addCookie(ResponseCookie.from(COOKIE_NAME, secret)
                .httpOnly(true).sameSite("Lax").secure(secure).path("/").maxAge(Duration.ofDays(365)).build());
        }
        if (principal instanceof Authentication authentication && trust.isAuthenticated(authentication)) {
            var username = authentication.getName();
            var user = users.getUser(username).block();
            if (user == null || user.getSpec() == null || Boolean.TRUE.equals(user.getSpec().getDisabled())) {
                throw new BusinessException(BusinessErrorCode.ACCESS_DENIED, "账号暂不可用");
            }
            var displayName = user.getSpec().getDisplayName();
            if (displayName == null || displayName.isBlank()) displayName = "已登录用户";
            var chef = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_super-role".equals(a.getAuthority()));
            var avatarUrl = user.getSpec().getAvatar();
            return new Identity(hash("user:" + username), username, displayName, false, chef,
                avatarUrl == null ? "" : avatarUrl.trim());
        }
        return new Identity(hash("guest:" + secret), null, "", true, false, "");
    }

    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
