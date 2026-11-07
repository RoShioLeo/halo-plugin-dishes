package com.dishes.service;

import com.dishes.ExtensionSchemeRegistry;
import com.dishes.extension.DishesSettings;
import com.dishes.domain.service.MealSchedule;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

@Service
public class DishesSettingsService {

    public static final String SETTINGS_NAME = "default";
    public static final String DEFAULT_PUBLIC_PATH = "/dishes";

    private final ReactiveExtensionClient client;
    private final ExtensionSchemeRegistry extensionSchemeRegistry;

    public DishesSettingsService(ReactiveExtensionClient client, ExtensionSchemeRegistry extensionSchemeRegistry) {
        this.client = client;
        this.extensionSchemeRegistry = extensionSchemeRegistry;
    }

    public DishesSettings getOrInitSettings() {
        extensionSchemeRegistry.ensureRegistered();
        var existing = withIndexRepair(() -> client.fetch(DishesSettings.class, SETTINGS_NAME).block());
        if (existing != null) {
            ensureSpecDefaults(existing);
            return existing;
        }
        var created = new DishesSettings();
        var metadata = new Metadata();
        metadata.setName(SETTINGS_NAME);
        created.setMetadata(metadata);
        created.setSpec(defaultSpec());
        var saved = withIndexRepair(() -> client.create(created).block());
        if (saved == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "create settings failed");
        }
        return saved;
    }

    public DishesSettings saveSettings(DishesSettings settings) {
        extensionSchemeRegistry.ensureRegistered();
        var saved = withIndexRepair(() -> client.update(settings).block());
        if (saved == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "update settings failed");
        }
        return saved;
    }

    private <T> T withIndexRepair(Supplier<T> action) {
        try {
            return action.get();
        } catch (Throwable ex) {
            if (containsNoIndicesError(ex)) {
                extensionSchemeRegistry.rebuildAll();
                return action.get();
            }
            throw ex;
        }
    }

    private boolean containsNoIndicesError(Throwable throwable) {
        var cursor = throwable;
        while (cursor != null) {
            var msg = cursor.getMessage();
            if (msg != null && msg.contains("No indices found for type")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    public DishesSettings.Spec safeReadSpec() {
        try {
            return getOrInitSettings().getSpec();
        } catch (Exception ignored) {
            return defaultSpec();
        }
    }

    public MealSchedule mealSchedule() {
        return MealSchedule.normalize(getOrInitSettings().getSpec().getMealSchedule());
    }

    public String normalizeAccessMode(String mode) {
        return "password".equals(mode) ? mode : "none";
    }

    public String normalizePublicAccessUrl(String raw) {
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
        if ("/".equals(v)) return "";
        return v;
    }

    public String normalizeLogoUrl(String raw) {
        return raw == null ? "" : raw.trim();
    }

    public String normalizePublicText(String raw, int maxLen) {
        if (raw == null) {
            return "";
        }
        var v = raw.trim();
        if (v.isEmpty()) {
            return "";
        }
        if (maxLen <= 0) {
            return "";
        }
        return v.length() > maxLen ? v.substring(0, maxLen) : v;
    }

    public String normalizeDomainWhitelist(String raw) {
        if (raw == null) return "";
        return raw
            .replace("\r\n", "\n")
            .replace(",", "\n")
            .lines()
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .distinct()
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
    }

    public String normalizePublicPathForPage(String raw) {
        var normalized = normalizePublicAccessUrl(raw);
        return normalized.isBlank() ? DEFAULT_PUBLIC_PATH : normalized;
    }

    private void ensureSpecDefaults(DishesSettings ext) {
        if (ext.getSpec() != null) return;
        ext.setSpec(defaultSpec());
    }

    private DishesSettings.Spec defaultSpec() {
        var spec = new DishesSettings.Spec();
        spec.setAccessMode("none");
        spec.setAccessPassword("");
        spec.setPublicAccessUrl("");
        spec.setPublicLogoUrl("");
        spec.setPublicSiteTitle("");
        spec.setPublicBrandTitle("");
        spec.setPublicBrandSubtitle("");
        spec.setPublicDomainWhitelist("");
        spec.setMealSchedule(MealSchedule.DEFAULT);
        return spec;
    }
}

