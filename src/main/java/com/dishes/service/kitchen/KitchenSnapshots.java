package com.dishes.service.kitchen;



import com.dishes.extension.MealTicketState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;

/** Detached command snapshots ensure failed persistence cannot mutate a live read or cached entity. */
public final class KitchenSnapshots {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private KitchenSnapshots() {}
    public static <T> T copy(T value, Class<T> type) {
        return value == null ? null : JSON.convertValue(value, type);
    }
    public static KitchenState copy(KitchenState state) {
        var sources = new ArrayList<MealSource>();
        if (state.contributions() != null) for (var source : state.contributions()) {
            sources.add(copy(source, MealSource.class));
        }
        return new KitchenState(copy(state.ticket(), MealTicketState.class), state.existing(), sources,
            copy(state.menu(), MealMenu.class));
    }
    public static KitchenState aggregate(MealTicketState ticket) {
        var sources = new ArrayList<MealSource>();
        for (var stored : ticket.getSpec().getSources()) {
            sources.add(copy(stored, MealSource.class));
        }
        MealMenu menu = null;
        if (ticket.getSpec().getConfirmedMenu() != null) {
            menu = new MealMenu(); menu.setSpec(copy(ticket.getSpec().getConfirmedMenu(), MealMenu.Spec.class));
        }
        return new KitchenState(copy(ticket, MealTicketState.class), true, sources, menu);
    }
}
