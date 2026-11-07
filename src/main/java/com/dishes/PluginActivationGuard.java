package com.dishes;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;
import run.halo.app.core.extension.Plugin;
import run.halo.app.extension.ReactiveExtensionClient;

/** Check exclusivity before controllers or business extension schemes initialize. */
@Component
public final class PluginActivationGuard implements BeanFactoryPostProcessor, PriorityOrdered {
    private ReactiveExtensionClient client;
    private boolean verified;

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        client = beanFactory.getBean(ReactiveExtensionClient.class);
        ensureExclusive();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    public synchronized void ensureExclusive() {
        if (verified) return;
        if (client == null) throw new IllegalStateException("Plugin activation has not been initialized");
        var legacy = client.fetch(Plugin.class, "dishes").block();
        if (legacy != null) {
            var enabled = legacy.getSpec() != null && Boolean.TRUE.equals(legacy.getSpec().getEnabled());
            var phase = legacy.getStatus() == null ? null : legacy.getStatus().getPhase();
            if (enabled || phase == Plugin.Phase.STARTING || phase == Plugin.Phase.STARTED
                || phase == Plugin.Phase.DISABLING) {
                throw new IllegalStateException("请先停用旧版 dishes 插件，等待其停止后再启用柠檬私厨");
            }
        }
        verified = true;
    }
}
