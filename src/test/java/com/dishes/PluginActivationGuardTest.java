package com.dishes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Plugin;
import run.halo.app.extension.ReactiveExtensionClient;

class PluginActivationGuardTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);

    @Test
    void missingLegacyPluginIsCheckedOnlyOnceWithoutWritingAnything() {
        when(client.fetch(Plugin.class, "dishes")).thenReturn(Mono.empty());
        var guard = new PluginActivationGuard();
        guard.postProcessBeanFactory(factory());
        guard.ensureExclusive();
        verify(client).fetch(Plugin.class, "dishes");
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @EnumSource(value = Plugin.Phase.class, names = {"DISABLED", "STOPPED", "FAILED", "RESOLVED"})
    void stoppedLegacyRecordCanRemainInstalled(Plugin.Phase phase) {
        when(client.fetch(Plugin.class, "dishes")).thenReturn(Mono.just(legacy(false, phase)));
        new PluginActivationGuard().postProcessBeanFactory(factory());
        verify(client).fetch(Plugin.class, "dishes");
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @EnumSource(value = Plugin.Phase.class, names = {"STARTING", "STARTED", "DISABLING"})
    void anActiveOrStoppingLegacyPluginIsRejectedEvenAfterItsToggleWasTurnedOff(Plugin.Phase phase) {
        when(client.fetch(Plugin.class, "dishes")).thenReturn(Mono.just(legacy(false, phase)));
        assertTrue(assertThrows(IllegalStateException.class,
            () -> new PluginActivationGuard().postProcessBeanFactory(factory())).getMessage().contains("先停用"));
        verify(client).fetch(Plugin.class, "dishes");
        verifyNoMoreInteractions(client);
    }

    @Test
    void anEnabledLegacyRecordIsRejectedBeforeAnyControllerSingletonInitializes() {
        when(client.fetch(Plugin.class, "dishes")).thenReturn(Mono.just(legacy(true, Plugin.Phase.STOPPED)));
        var controllers = new AtomicInteger();
        try (var context = new GenericApplicationContext()) {
            context.registerBean(ReactiveExtensionClient.class, () -> client);
            context.registerBean(PluginActivationGuard.class);
            context.registerBean("controllerSentinel", Object.class, () -> {
                controllers.incrementAndGet();
                return new Object();
            });
            assertThrows(IllegalStateException.class, context::refresh);
        }
        assertEquals(0, controllers.get());
        verify(client).fetch(Plugin.class, "dishes");
        verifyNoMoreInteractions(client);
    }

    @Test
    void failedLegacyLookupCannotSilentlyAllowActivation() {
        when(client.fetch(Plugin.class, "dishes")).thenReturn(Mono.error(new IllegalStateException("storage unavailable")));
        assertThrows(IllegalStateException.class, () -> new PluginActivationGuard().postProcessBeanFactory(factory()));
        verify(client).fetch(Plugin.class, "dishes");
        verifyNoMoreInteractions(client);
    }

    private ConfigurableListableBeanFactory factory() {
        var factory = mock(ConfigurableListableBeanFactory.class);
        when(factory.getBean(ReactiveExtensionClient.class)).thenReturn(client);
        return factory;
    }

    private Plugin legacy(boolean enabled, Plugin.Phase phase) {
        var plugin = new Plugin();
        var spec = new Plugin.PluginSpec(); spec.setEnabled(enabled); plugin.setSpec(spec);
        var status = new Plugin.PluginStatus(); status.setPhase(phase); plugin.setStatus(status);
        return plugin;
    }
}
