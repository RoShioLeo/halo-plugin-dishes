package com.dishes.service.dto;

import com.dishes.extension.DishesSettings;
import java.util.Map;

public final class ApiPayloads {

    private ApiPayloads() {}

    public static Map<String, Object> idCreated(long id) {
        return Map.of("id", id);
    }

    public static Map<String, Object> updated(long id) {
        return Map.of("updated", true, "id", id);
    }

    public static Map<String, Object> deleted(long id) {
        return Map.of("deleted", true, "id", id);
    }

    public static Map<String, Object> updatedOnly() {
        return Map.of("updated", true);
    }

    public static Map<String, Object> itemList(Object items) {
        return Map.of("items", items);
    }

    public static Map<String, Object> settingsPayload(DishesSettings.Spec spec, String mode) {
        var basic = new java.util.LinkedHashMap<String, Object>();
        basic.put("accessMode", mode);
        basic.put("accessPassword", spec.getAccessPassword() == null ? "" : spec.getAccessPassword());
        basic.put("accessPasswordSet", spec.getAccessPassword() != null && !spec.getAccessPassword().isBlank());
        basic.put("publicAccessUrl", spec.getPublicAccessUrl() == null ? "" : spec.getPublicAccessUrl());
        basic.put("publicLogoUrl", spec.getPublicLogoUrl() == null ? "" : spec.getPublicLogoUrl());
        basic.put("publicSiteTitle", spec.getPublicSiteTitle() == null ? "" : spec.getPublicSiteTitle());
        basic.put("publicBrandTitle", spec.getPublicBrandTitle() == null ? "" : spec.getPublicBrandTitle());
        basic.put("publicBrandSubtitle", spec.getPublicBrandSubtitle() == null ? "" : spec.getPublicBrandSubtitle());
        basic.put("publicDomainWhitelist", spec.getPublicDomainWhitelist() == null ? "" : spec.getPublicDomainWhitelist());
        basic.put("defaultPublicAccessUrl", "/dishes");
        basic.put("mealSchedule", com.dishes.domain.service.MealSchedule.normalize(spec.getMealSchedule()));
        return Map.of("basic", basic);
    }
}