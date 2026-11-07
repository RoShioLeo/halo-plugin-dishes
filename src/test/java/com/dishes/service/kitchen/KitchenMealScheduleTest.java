package com.dishes.service.kitchen;

import static com.dishes.service.kitchen.KitchenModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.dishes.domain.DishesStore;
import com.dishes.domain.service.MealSchedule;
import com.dishes.service.DishesSettingsService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KitchenMealScheduleTest {
    private final KitchenRepository repository = mock(KitchenRepository.class);
    private final DishesStore catalogue = mock(DishesStore.class);
    private final DishesSettingsService settings = mock(DishesSettingsService.class);
    private final Identity guest = new Identity("guest", null, "", true, false, "");
    private final Identity chef = new Identity("chef", "admin", "主厨", false, true, "");

    @Test
    void viewerUsesShanghaiServerTimeAndTheSameConfiguredScheduleAtEachBoundary() {
        when(settings.mealSchedule()).thenReturn(MealSchedule.DEFAULT);
        assertEquals("breakfast", at("2026-11-05T01:59:59Z").viewer(guest).get("defaultPeriod"));
        assertEquals("lunch", at("2026-11-05T02:00:00Z").viewer(guest).get("defaultPeriod"));
        assertEquals("lunch", at("2026-11-05T06:59:59Z").viewer(guest).get("defaultPeriod"));
        var viewer = at("2026-11-05T07:00:00Z").viewer(guest);
        assertEquals("dinner", viewer.get("defaultPeriod")); assertEquals(MealSchedule.DEFAULT, viewer.get("mealSchedule"));
        assertEquals("2026-11-05", at("2026-11-04T16:30:00Z").viewer(guest).get("today"));
    }

    @Test
    void kitchenAndViewerShareTheConfiguredDefaultIndependentOfSelectedMealDate() {
        var schedule = new MealSchedule("11:45", "18:15");
        when(settings.mealSchedule()).thenReturn(schedule);
        when(repository.load(any(), any())).thenAnswer(call -> new KitchenState(null, false, new ArrayList<>(), null));
        var kitchen = at("2026-11-05T09:00:00Z");
        var viewer = kitchen.viewer(guest); var workspace = kitchen.kitchen("2026-11-10", chef);
        assertEquals("lunch", workspace.get("defaultPeriod"));
        assertEquals(viewer.get("defaultPeriod"), workspace.get("defaultPeriod"));
        assertEquals(schedule, workspace.get("mealSchedule")); assertEquals("2026-11-10", workspace.get("date"));
        assertEquals(3, ((java.util.List<?>) workspace.get("periods")).size());
    }

    private KitchenService at(String instant) {
        return new KitchenService(repository, catalogue, settings, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
