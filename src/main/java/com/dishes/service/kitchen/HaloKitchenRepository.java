package com.dishes.service.kitchen;

import com.dishes.ExtensionSchemeRegistry;
import com.dishes.api.BusinessErrorCode;
import com.dishes.api.BusinessException;
import com.dishes.extension.MealTicketState;
import java.util.ArrayList;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import run.halo.app.extension.ReactiveExtensionClient;

@Component
public class HaloKitchenRepository implements KitchenRepository {
    private final ReactiveExtensionClient client;
    private final ExtensionSchemeRegistry registry;
    public HaloKitchenRepository(ReactiveExtensionClient client, ExtensionSchemeRegistry registry) {
        this.client = client; this.registry = registry;
    }
    public KitchenState load(String date, String period) {
        registry.ensureRegistered();
        var header = client.fetch(MealTicketState.class, date + "-" + period).block();
        return header == null ? new KitchenState(null, false, new ArrayList<>(), null) : KitchenSnapshots.aggregate(header);
    }
    public void saveState(KitchenState state) {
        var header = KitchenSnapshots.copy(state.ticket(), MealTicketState.class);
        header.getSpec().setSources(state.contributions().stream().map(source -> KitchenSnapshots.copy(source, MealSource.class)).toList());
        header.getSpec().setConfirmedMenu(state.menu() == null ? null : KitchenSnapshots.copy(state.menu().getSpec(), MealMenu.Spec.class));
        try {
            var result = state.existing() ? client.update(header).block() : client.create(header).block();
            if (result == null) throw new IllegalStateException("Meal persistence returned no value");
        } catch (OptimisticLockingFailureException ex) {
            throw conflict();
        } catch (RuntimeException ex) {
            // This Halo exception is outside its public API; map competing first writes to the same CAS conflict.
            if ("DuplicateNameException".equals(ex.getClass().getSimpleName())) throw conflict();
            throw ex;
        }
    }
    private BusinessException conflict() {
        return new BusinessException(BusinessErrorCode.CONFLICT, "内容已更新，请刷新后重试");
    }
}