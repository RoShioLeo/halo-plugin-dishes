package com.dishes;

import static run.halo.app.extension.index.IndexAttributeFactory.simpleAttribute;

import com.dishes.extension.Dish;
import com.dishes.extension.DishCategory;
import com.dishes.extension.DishesSettings;
import com.dishes.extension.MealTicketState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Extension;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.exception.SchemeNotFoundException;
import run.halo.app.extension.index.IndexSpec;

@Component
@SuppressWarnings("removal")
public class ExtensionSchemeRegistry {
    private static final Logger log = LoggerFactory.getLogger(ExtensionSchemeRegistry.class);

    private final SchemeManager schemeManager;
    private final PluginActivationGuard activationGuard;

    public ExtensionSchemeRegistry(SchemeManager schemeManager, PluginActivationGuard activationGuard) {
        this.schemeManager = schemeManager;
        this.activationGuard = activationGuard;
    }

    public synchronized void ensureRegistered() {
        activationGuard.ensureExclusive();
        ensureDishCategory();
        ensureDish();
        ensureDishesSettings();
        ensureKitchen();
    }

    public synchronized void rebuildAll() {
        activationGuard.ensureExclusive();
        unregisterIfPresent(DishCategory.class);
        unregisterIfPresent(Dish.class);
        unregisterIfPresent(DishesSettings.class);
        unregisterIfPresent(MealTicketState.class);
        ensureRegistered();
        log.warn("Rebuilt all extension schemes and indexes due to index inconsistency.");
    }

    /** Release this plugin instance's registrations, retaining persisted extension data. */
    public synchronized void unregisterOwnedSchemes() {
        for (var type : java.util.List.of(MealTicketState.class, DishesSettings.class, Dish.class, DishCategory.class)) {
            Scheme existing;
            try {
                existing = schemeManager.get(type);
            } catch (SchemeNotFoundException ex) {
                continue;
            }
            // An older plugin instance must not remove a newer instance's registration.
            if (existing.type() == type) {
                schemeManager.unregister(existing);
                log.info("Unregistered owned scheme for {}", type.getName());
            }
        }
    }

    private void ensureDishCategory() {
        if (hasScheme(DishCategory.class)) {
            return;
        }
        schemeManager.register(DishCategory.class, indexSpecs -> indexSpecs.add(new IndexSpec()
            .setName("spec.slug")
            .setIndexFunc(simpleAttribute(DishCategory.class, x ->
                x.getSpec() == null ? null : x.getSpec().getSlug()))
        ));
        log.info("Registered scheme for {}", DishCategory.class.getName());
    }

    private void ensureDish() {
        if (hasScheme(Dish.class)) {
            return;
        }
        schemeManager.register(Dish.class, indexSpecs -> indexSpecs.add(new IndexSpec()
            .setName("spec.categoryId")
            .setIndexFunc(simpleAttribute(Dish.class, x ->
                x.getSpec() == null || x.getSpec().getCategoryId() == null
                    ? null
                    : String.valueOf(x.getSpec().getCategoryId())))
        ));
        log.info("Registered scheme for {}", Dish.class.getName());
    }

    private void ensureDishesSettings() {
        if (hasScheme(DishesSettings.class)) {
            return;
        }
        schemeManager.register(DishesSettings.class);
        log.info("Registered scheme for {}", DishesSettings.class.getName());
    }

    private void ensureKitchen() {
        if (!hasScheme(MealTicketState.class)) schemeManager.register(MealTicketState.class);
    }

    private boolean hasScheme(Class<? extends Extension> type) {
        Scheme existing;
        try {
            existing = schemeManager.get(type);
        } catch (SchemeNotFoundException ex) {
            return false;
        }
        if (existing.type() == type) return true;
        // SchemeManager resolves by GVK, so hot upgrades can leave a previous classloader's type.
        schemeManager.unregister(existing);
        log.info("Replacing stale classloader scheme for {}", type.getName());
        return false;
    }

    private void unregisterIfPresent(Class<? extends Extension> type) {
        try {
            Scheme scheme = schemeManager.get(type);
            if (scheme != null) {
                schemeManager.unregister(scheme);
                log.info("Unregistered scheme for {}", type.getName());
            }
        } catch (SchemeNotFoundException ex) {
            log.debug("Scheme already absent for {}", type.getName());
        }
    }
}
