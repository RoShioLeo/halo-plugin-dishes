package com.dishes.domain.service;

import com.dishes.api.BusinessErrorCode;
import com.dishes.api.BusinessException;
import com.dishes.domain.DishesStore;
import java.util.LinkedHashSet;
import java.util.List;

/** LemonSlice multi-tag catalogue, modified 2026-11-05 under GPL-3.0. */
public final class DishTags {
    public static final List<String> MEAL_TAGS = List.of("早餐", "午餐", "晚餐");
    private DishTags() {}

    public static List<String> normalize(List<String> tags) {
        if (tags == null) tags = List.of();
        var unique = new LinkedHashSet<String>();
        for (var raw : tags) {
            if (raw == null || raw.isBlank()) continue;
            var tag = raw.trim();
            if (tag.length() > 24 || tag.codePoints().anyMatch(Character::isISOControl)) {
                throw new BusinessException(BusinessErrorCode.INVALID_REQUEST, "标签最多 24 个字符，且不能包含控制字符");
            }
            unique.add(tag);
        }
        if (unique.containsAll(MEAL_TAGS)) unique.removeAll(MEAL_TAGS);
        if (unique.size() > 16) throw new BusinessException(BusinessErrorCode.INVALID_REQUEST, "每道菜最多 16 个标签");
        return List.copyOf(unique);
    }

    public static List<String> suggestions(List<DishesStore.Dish> dishes) {
        var tags = new LinkedHashSet<>(MEAL_TAGS);
        for (var dish : dishes) tags.addAll(dish.tags());
        return List.copyOf(tags);
    }

    public static boolean applicable(List<String> tags, String period) {
        if (period == null) return false;
        var mealTag = switch (period) {
            case "breakfast" -> "早餐";
            case "lunch" -> "午餐";
            case "dinner" -> "晚餐";
            default -> null;
        };
        if (mealTag == null) return false;
        if (tags == null || tags.stream().noneMatch(MEAL_TAGS::contains)) return true;
        return tags.contains(mealTag);
    }
}
