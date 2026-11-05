package com.dishes.service;

import com.dishes.domain.DishesStore;
import com.dishes.domain.service.DishTags;
import com.dishes.service.publics.PublicAccessService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PublicFacadeService {

    private final DishesStore store;
    private final PublicAccessService publicAccessService;

    public PublicFacadeService(DishesStore store, PublicAccessService publicAccessService) {
        this.store = store;
        this.publicAccessService = publicAccessService;
    }

    public Map<String, Object> dishes(Long categoryId, String token) {
        publicAccessService.ensureAccess(token);
        return Map.of("items", store.listDishes(categoryId), "tags", DishTags.suggestions(store.listDishes(null)));
    }

    public Map<String, Object> accessStatus(String token) {
        return publicAccessService.accessStatus(token);
    }

    public Map<String, Object> verifyPassword(String password) {
        return publicAccessService.verifyPassword(password);
    }
}

