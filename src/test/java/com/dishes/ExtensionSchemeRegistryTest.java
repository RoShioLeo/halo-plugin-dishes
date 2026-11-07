package com.dishes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.dishes.extension.Dish;
import com.dishes.extension.DishCategory;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.Extension;
import run.halo.app.extension.GVK;
import run.halo.app.extension.GroupVersionKind;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.PluginContext;

class ExtensionSchemeRegistryTest {
    @Test
    void replacesSameGvkFromAnotherClassloaderBeforeRegisteringCurrentType() {
        var manager = new TrackingSchemeManager();
        manager.register(OtherClassloaderDish.class);
        var old = manager.get(Dish.class);

        registry(manager).ensureRegistered();

        assertSame(Dish.class, manager.get(Dish.class).type());
        assertTrue(manager.removals.contains(old));
        assertEquals(4, manager.schemes().size());
        assertEquals(1, manager.registrations.stream().filter(type -> type == Dish.class).count());
    }

    @Test
    void retainsCurrentClassRegistrationsOnRepeatedReads() {
        var manager = new TrackingSchemeManager();
        var registry = registry(manager);
        registry.ensureRegistered();
        manager.registrations.clear();

        registry.ensureRegistered();

        assertTrue(manager.registrations.isEmpty());
        assertTrue(manager.removals.isEmpty());
        assertEquals(4, manager.schemes().size());
    }

    @Test
    void stopCleanupRemovesOnlySchemesOwnedByCurrentClassIdentities() {
        var manager = new TrackingSchemeManager();
        var registry = registry(manager);
        registry.ensureRegistered();
        manager.unregister(manager.get(DishCategory.class));
        manager.register(OtherInstanceCategory.class);
        var otherOwner = manager.get(DishCategory.class);
        manager.removals.clear();

        registry.unregisterOwnedSchemes();

        assertEquals(List.of(otherOwner), manager.schemes());
        assertEquals(3, manager.removals.size());
        assertFalse(manager.removals.contains(otherOwner));
    }

    @Test
    void pluginStopDelegatesRegistrationCleanupWithoutDeletingBusinessExtensions() {
        var manager = new TrackingSchemeManager();
        var registry = registry(manager);
        registry.ensureRegistered();
        var businessCalls = new AtomicInteger();
        var client = (ReactiveExtensionClient) Proxy.newProxyInstance(
            ReactiveExtensionClient.class.getClassLoader(), new Class<?>[]{ReactiveExtensionClient.class},
            (proxy, method, arguments) -> {
                businessCalls.incrementAndGet();
                throw new AssertionError("Lifecycle cleanup must not call business storage APIs");
            });
        var plugin = new DishesPlugin(PluginContext.builder().name("test-dishes").build(), registry, client);

        plugin.stop();

        assertTrue(manager.schemes().isEmpty());
        assertEquals(4, manager.removals.size());
        assertEquals(0, businessCalls.get());
    }

    @Test
    void aStaleRegistrationCleanupFailureIsNotSilentlyTreatedAsAnAbsentScheme() {
        var manager = new TrackingSchemeManager();
        manager.register(OtherClassloaderDish.class);
        manager.failUnregister = true;

        assertThrows(IllegalStateException.class,
            () -> registry(manager).ensureRegistered());
        assertSame(OtherClassloaderDish.class, manager.get(Dish.class).type());
        assertFalse(manager.registrations.contains(Dish.class));
    }

    @GVK(group = "dishes.plugin.halo.run", version = "v1alpha1", kind = "Dish",
        plural = "dishes", singular = "dish")
    static class OtherClassloaderDish extends AbstractExtension {}

    @GVK(group = "dishes.plugin.halo.run", version = "v1alpha1", kind = "DishCategory",
        plural = "dishcategories", singular = "dishcategory")
    static class OtherInstanceCategory extends AbstractExtension {}

    @Test
    void activationRefusalDoesNotReplaceOrRebuildExistingRegistrations() {
        var manager = new TrackingSchemeManager();
        manager.register(OtherClassloaderDish.class);
        var old = manager.get(Dish.class);
        var guard = mock(PluginActivationGuard.class);
        doThrow(new IllegalStateException("old plugin remains active")).when(guard).ensureExclusive();
        var registry = new ExtensionSchemeRegistry(manager, guard);

        assertThrows(IllegalStateException.class, registry::ensureRegistered);
        assertThrows(IllegalStateException.class, registry::rebuildAll);

        assertSame(old, manager.get(Dish.class));
        assertTrue(manager.removals.isEmpty());
        assertEquals(List.of(OtherClassloaderDish.class), manager.registrations);
    }

    private ExtensionSchemeRegistry registry(TrackingSchemeManager manager) {
        return new ExtensionSchemeRegistry(manager, mock(PluginActivationGuard.class));
    }

    private static class TrackingSchemeManager implements SchemeManager {
        private final List<Scheme> values = new ArrayList<>();
        final List<Scheme> removals = new ArrayList<>();
        final List<Class<? extends Extension>> registrations = new ArrayList<>();
        boolean failUnregister;

        @Override
        public <E extends Extension> void register(Class<E> type, Consumer<IndexSpecs<E>> indexes) {
            var gvk = Scheme.getGvkFromType(type);
            values.add(new Scheme(type, new GroupVersionKind(gvk.group(), gvk.version(), gvk.kind()),
                gvk.plural(), gvk.singular(), JsonNodeFactory.instance.objectNode()));
            registrations.add(type);
        }

        @Override
        public void unregister(Scheme scheme) {
            if (failUnregister) throw new IllegalStateException("Index registration cleanup failed");
            removals.add(scheme);
            values.remove(scheme);
        }

        @Override
        public List<Scheme> schemes() { return List.copyOf(values); }
    }
}
