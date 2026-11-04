package com.dishes;

import com.dishes.service.DishesSettingsService;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.net.URI;
import java.net.URISyntaxException;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import run.halo.app.theme.TemplateNameResolver;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration(proxyBeanMethods = false)
public class SiteRouter {

    private static final String DEFAULT_PUBLIC_PATH = "/dishes";
    private static final String DEFAULT_PUBLIC_LOGO_URL = "";
    private static final String DEFAULT_SITE_TITLE = "柠檬私厨";
    private static final String DEFAULT_BRAND_TITLE = "柠檬私厨";
    private static final String DEFAULT_BRAND_SUBTITLE = "今天，想吃点什么？";
    private static final AtomicReference<String> CONFIGURED_PUBLIC_PATH = new AtomicReference<>("");
    private static final AtomicReference<String> CONFIGURED_PUBLIC_LOGO_URL = new AtomicReference<>("");
    private static final AtomicReference<String> CONFIGURED_PUBLIC_SITE_TITLE = new AtomicReference<>("");
    private static final AtomicReference<String> CONFIGURED_PUBLIC_BRAND_TITLE = new AtomicReference<>("");
    private static final AtomicReference<String> CONFIGURED_PUBLIC_BRAND_SUBTITLE = new AtomicReference<>("");
    private final TemplateNameResolver templateNameResolver;
    private final DishesSettingsService settingsService;

    public SiteRouter(TemplateNameResolver templateNameResolver, DishesSettingsService settingsService) {
        this.templateNameResolver = templateNameResolver;
        this.settingsService = settingsService;
    }

    @Bean
    RouterFunction<ServerResponse> dishesPageRouter() {
        return route(matchesDishesPage(), this::renderDishesPage);
    }

    @PostConstruct
    void initConfiguredPathCache() {
        updateConfiguredPublicPath(readConfiguredPublicPath());
        updateConfiguredPublicLogoUrl(readConfiguredPublicLogoUrl());
        var spec = settingsService.safeReadSpec();
        updateConfiguredPublicBranding(
            spec.getPublicSiteTitle() == null ? "" : spec.getPublicSiteTitle(),
            spec.getPublicBrandTitle() == null ? "" : spec.getPublicBrandTitle(),
            spec.getPublicBrandSubtitle() == null ? "" : spec.getPublicBrandSubtitle()
        );
    }

    public static void updateConfiguredPublicPath(String raw) {
        var normalized = normalizePathStatic(raw);
        CONFIGURED_PUBLIC_PATH.set(normalized);
    }

    public static void updateConfiguredPublicLogoUrl(String raw) {
        if (raw == null) {
            CONFIGURED_PUBLIC_LOGO_URL.set(DEFAULT_PUBLIC_LOGO_URL);
            return;
        }
        CONFIGURED_PUBLIC_LOGO_URL.set(raw.trim());
    }

    public static void updateConfiguredPublicBranding(String siteTitle, String brandTitle, String brandSubtitle) {
        CONFIGURED_PUBLIC_SITE_TITLE.set(siteTitle == null ? "" : siteTitle.trim());
        CONFIGURED_PUBLIC_BRAND_TITLE.set(brandTitle == null ? "" : brandTitle.trim());
        CONFIGURED_PUBLIC_BRAND_SUBTITLE.set(brandSubtitle == null ? "" : brandSubtitle.trim());
    }

    private static String effectiveWithDefault(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        var v = raw.trim();
        return v.isEmpty() ? fallback : v;
    }

    private reactor.core.publisher.Mono<ServerResponse> renderDishesPage(ServerRequest request) {
        var basePath = resolveBasePathByRequest(request.path());
        if (basePath == null) {
            return ServerResponse.notFound().build();
        }
        var model = new HashMap<String, Object>();
        var pageTitle = effectiveWithDefault(CONFIGURED_PUBLIC_SITE_TITLE.get(), DEFAULT_SITE_TITLE);
        var brandTitle = effectiveWithDefault(CONFIGURED_PUBLIC_BRAND_TITLE.get(), DEFAULT_BRAND_TITLE);
        var brandSubtitle = effectiveWithDefault(CONFIGURED_PUBLIC_BRAND_SUBTITLE.get(), DEFAULT_BRAND_SUBTITLE);
        model.put("title", pageTitle);
        model.put("brandTitle", brandTitle);
        model.put("brandSubtitle", brandSubtitle);
        model.put("publicBasePath", basePath);
        model.put("publicLogoUrl", configuredPublicLogoUrl());
        return templateNameResolver
            .resolveTemplateNameOrDefault(request.exchange(), "dishes")
            .flatMap(templateName -> ServerResponse.ok().render(templateName, model));
    }

    private RequestPredicate matchesDishesPage() {
        return request -> "GET".equalsIgnoreCase(request.method().name()) && resolveBasePathByRequest(request.path()) != null;
    }

    private String resolveBasePathByRequest(String requestPath) {
        var path = normalizePath(requestPath);
        var customPath = configuredPublicPath();
        var hasCustom = !Objects.equals(customPath, DEFAULT_PUBLIC_PATH);
        if (hasCustom) {
            if (matchesPath(path, customPath)) return customPath;
            return null;
        }
        if (matchesPath(path, DEFAULT_PUBLIC_PATH)) return DEFAULT_PUBLIC_PATH;
        return null;
    }

    private boolean matchesPath(String path, String base) {
        return path.equals(base) || path.startsWith(base + "/");
    }

    private String configuredPublicPath() {
        var normalized = normalizePath(CONFIGURED_PUBLIC_PATH.get());
        if (normalized.isBlank() || "/".equals(normalized)) return DEFAULT_PUBLIC_PATH;
        return normalized;
    }

    private String readConfiguredPublicPath() {
        var spec = settingsService.safeReadSpec();
        return spec.getPublicAccessUrl() == null ? "" : spec.getPublicAccessUrl();
    }

    private String readConfiguredPublicLogoUrl() {
        var spec = settingsService.safeReadSpec();
        return spec.getPublicLogoUrl() == null ? "" : spec.getPublicLogoUrl();
    }

    private String configuredPublicLogoUrl() {
        var v = CONFIGURED_PUBLIC_LOGO_URL.get();
        return v == null ? DEFAULT_PUBLIC_LOGO_URL : v.trim();
    }

    private String normalizePath(String raw) {
        return normalizePathStatic(raw);
    }

    private static String normalizePathStatic(String raw) {
        if (raw == null) return "";
        var v = raw.trim();
        if (v.isBlank()) return "";
        if (v.startsWith("http://") || v.startsWith("https://")) {
            try {
                v = new URI(v).getPath();
            } catch (URISyntaxException ignored) {
                return "";
            }
        }
        if (v == null || v.isBlank()) return "";
        if (!v.startsWith("/")) v = "/" + v;
        if (v.length() > 1 && v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }
}


