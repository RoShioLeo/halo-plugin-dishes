package com.dishes.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import com.dishes.api.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class DishTagsTest {
    @Test
    void multiTagsTrimDeduplicateAndDoNotRequireMealTags() {
        assertEquals(List.of("午餐", "清淡", "晚餐"), DishTags.normalize(List.of(" 午餐 ", "清淡", "午餐", "晚餐")));
        assertEquals(List.of(), DishTags.normalize(List.of()));
        assertEquals(List.of(), DishTags.normalize(null));
        assertEquals(List.of("早餐", "午餐", "晚餐"), DishTags.suggestions(List.of()));
        new PersistentDishesDomainService().validateDishInput("一道菜", "", 5);
        assertThrows(BusinessException.class, () -> DishTags.normalize(List.of("长".repeat(25))));
        assertThrows(BusinessException.class, () -> DishTags.normalize(java.util.stream.IntStream.range(0, 17).mapToObj(Integer::toString).toList()));
    }

    @Test
    void mealTagsRestrictMatchingPeriodsAndUntaggedDishesAreUniversal() {
        assertTrue(DishTags.applicable(List.of("早餐", "午餐", "清淡"), "breakfast"));
        assertTrue(DishTags.applicable(List.of("早餐", "午餐", "清淡"), "lunch"));
        assertFalse(DishTags.applicable(List.of("早餐", "午餐", "清淡"), "dinner"));
        for (var period : List.of("breakfast", "lunch", "dinner")) {
            assertTrue(DishTags.applicable(List.of(), period));
            assertTrue(DishTags.applicable(List.of("清淡", "辣"), period));
        }
        assertFalse(DishTags.applicable(List.of(), "unknown"));
    }

    @Test
    void selectingEveryMealNormalizesToAllDayAndPreservesCustomTags() {
        var normalized = DishTags.normalize(List.of("清淡", " 早餐 ", "午餐", "辣", "晚餐", "早餐"));
        assertEquals(List.of("清淡", "辣"), normalized);
        assertEquals(List.of(), DishTags.normalize(List.of("晚餐", "早餐", "午餐")));
        assertEquals(normalized, DishTags.normalize(normalized));
        for (var period : List.of("breakfast", "lunch", "dinner")) {
            assertTrue(DishTags.applicable(normalized, period));
        }
    }

    @Test
    void partialMealSelectionRetainsItsPeriodRestriction() {
        var normalized = DishTags.normalize(List.of(" 早餐 ", "清淡", "午餐", "早餐"));
        assertEquals(List.of("早餐", "清淡", "午餐"), normalized);
        assertTrue(DishTags.applicable(normalized, "breakfast"));
        assertTrue(DishTags.applicable(normalized, "lunch"));
        assertFalse(DishTags.applicable(normalized, "dinner"));
    }

    @Test
    void equivalentAllDayInputUsesTheSameCustomTagLimit() {
        var custom = java.util.stream.IntStream.range(0, 16).mapToObj(index -> "口味" + index).toList();
        var allMealsAndCustom = new java.util.ArrayList<>(DishTags.MEAL_TAGS);
        allMealsAndCustom.addAll(custom);
        assertEquals(custom, DishTags.normalize(allMealsAndCustom));
        allMealsAndCustom.add("超出上限");
        assertThrows(BusinessException.class, () -> DishTags.normalize(allMealsAndCustom));
    }
}
