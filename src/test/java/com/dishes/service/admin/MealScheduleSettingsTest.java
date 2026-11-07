package com.dishes.service.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.dishes.ExtensionSchemeRegistry;
import com.dishes.api.BusinessException;
import com.dishes.domain.service.MealSchedule;
import com.dishes.extension.DishesSettings;
import com.dishes.service.DishesSettingsService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class MealScheduleSettingsTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final DishesSettingsService settings = new DishesSettingsService(client, mock(ExtensionSchemeRegistry.class));
    private final AdminSettingsService admin = new AdminSettingsService(settings);

    @Test
    void scheduleRoundtripsInBasicSettingsWithPasswordAndBrandingRetained() {
        var extension = extension();
        when(client.fetch(DishesSettings.class, "default")).thenReturn(Mono.just(extension));
        when(client.update(any(DishesSettings.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        var schedule = new MealSchedule("11:30", "18:15");
        admin.updateSettings("password", "test-password", "/dishes", "/lemon.svg", "私厨", "柠檬私厨", "今天吃什么", "blog.lemonslice.cloud", schedule);
        var basic = (Map<String, Object>) admin.getSettings().get("basic");
        assertEquals(schedule, basic.get("mealSchedule")); assertEquals(schedule, settings.mealSchedule());
        assertEquals("test-password", basic.get("accessPassword")); assertEquals("柠檬私厨", basic.get("publicBrandTitle"));
        assertEquals("blog.lemonslice.cloud", basic.get("publicDomainWhitelist"));
        assertEquals(schedule, extension.getSpec().getMealSchedule());
        verify(client, times(1)).update(extension);
    }

    @Test
    void omittedScheduleUsesTheDefaultWithoutWritingSettings() {
        var extension = extension();
        when(client.fetch(DishesSettings.class, "default")).thenReturn(Mono.just(extension));
        assertEquals(MealSchedule.DEFAULT, settings.mealSchedule());
        assertEquals(MealSchedule.DEFAULT, ((Map<?, ?>) admin.getSettings().get("basic")).get("mealSchedule"));
        assertNull(extension.getSpec().getMealSchedule());
        verify(client, never()).update(any()); verify(client, never()).create(any());
    }

    @Test
    void invalidScheduleIsRejectedBeforeAnySettingsOrRouterMutation() {
        assertThrows(BusinessException.class, () -> admin.updateSettings("password", "changed", "/changed", "", "", "", "", "",
            new MealSchedule("18:00", "12:00")));
        verifyNoInteractions(client);
    }

    private DishesSettings extension() {
        var extension = new DishesSettings(); var metadata = new Metadata(); metadata.setName("default"); extension.setMetadata(metadata);
        var spec = new DishesSettings.Spec(); spec.setAccessMode("none"); extension.setSpec(spec); return extension;
    }
}
