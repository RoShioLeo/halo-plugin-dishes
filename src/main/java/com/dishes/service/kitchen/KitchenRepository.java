package com.dishes.service.kitchen;

public interface KitchenRepository {
    KitchenState load(String date, String period);
    /** One authoritative extension CAS persists sources, batch lock and published menu together. */
    void saveState(KitchenState state);
}