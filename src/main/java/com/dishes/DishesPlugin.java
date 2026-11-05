package com.dishes;

import com.dishes.extension.DishesSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * 插件主类，管理插件的生命周期。
 */
@Component
public class DishesPlugin extends BasePlugin {
    private static final Logger log = LoggerFactory.getLogger(DishesPlugin.class);

    private final ExtensionSchemeRegistry extensionSchemeRegistry;
    private final ReactiveExtensionClient client;

    public DishesPlugin(PluginContext pluginContext, ExtensionSchemeRegistry extensionSchemeRegistry, ReactiveExtensionClient client) {
        super(pluginContext);
        this.extensionSchemeRegistry = extensionSchemeRegistry;
        this.client = client;
    }

    @Override
    public void start() {
        log.info("DishesPlugin.start() invoked, begin extension scheme registration.");
        extensionSchemeRegistry.ensureRegistered();
        log.info("Extension schemes and indexes registered successfully in DishesPlugin.start().");

        client.listAll(com.dishes.extension.DishCategory.class, ListOptions.builder().build(), org.springframework.data.domain.Sort.by("metadata.name"))
            .take(1)
            .collectList()
            .block();
        client.fetch(DishesSettings.class, "default").blockOptional();
        log.info("DishesPlugin.start() completed, extension scheme registration finished.");
    }

    @Override
    public void stop() {
        extensionSchemeRegistry.unregisterOwnedSchemes();
        log.info("Dishes plugin stopped.");
    }
}

