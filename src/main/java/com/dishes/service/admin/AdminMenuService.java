package com.dishes.service.admin;

import com.dishes.api.BusinessErrorCode;
import com.dishes.api.BusinessException;
import com.dishes.domain.DishesStore;
import com.dishes.domain.service.DishTags;
import com.dishes.service.dto.ApiPayloads;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminMenuService {

    private final DishesStore store;

    public AdminMenuService(DishesStore store) {
        this.store = store;
    }

    public Map<String, Object> listCategories() {
        return ApiPayloads.itemList(store.listCategories());
    }

    public Map<String, Object> createCategory(String name, String slug, int sortOrder) {
        var c = store.createCategory(name, slug, sortOrder);
        return ApiPayloads.idCreated(c.id());
    }

    public Map<String, Object> updateCategory(long id, String name, String slug, int sortOrder) {
        var c = store.updateCategory(id, name, slug, sortOrder);
        if (c == null) throw new BusinessException(BusinessErrorCode.NOT_FOUND, "notfound");
        return ApiPayloads.updated(id);
    }

    public Map<String, Object> deleteCategory(long id) {
        var ok = store.deleteCategory(id);
        if (!ok) {
            throw new BusinessException(
                BusinessErrorCode.CATEGORY_DELETE_CONFLICT,
                "cannot delete (category not found or still has dishes)"
            );
        }
        return ApiPayloads.deleted(id);
    }

    public Map<String, Object> listDishes() {
        var dishes = store.listDishes(null);
        return Map.of("items", dishes, "tags", DishTags.suggestions(dishes));
    }

    public Map<String, Object> createDish(
        long categoryId,
        String name,
        String imageUrl,
        int recommendationLevel,
        String description,
        boolean isAvailable,
        int sortOrder,
        List<String> tags,
        boolean soldOut
    ) {
        var d = store.createDish(categoryId, name, imageUrl, recommendationLevel, description, isAvailable, sortOrder, tags, soldOut);
        return ApiPayloads.idCreated(d.id());
    }

    public Map<String, Object> updateDish(
        long id,
        long categoryId,
        String name,
        String imageUrl,
        int recommendationLevel,
        String description,
        boolean isAvailable,
        int sortOrder,
        List<String> tags,
        boolean soldOut
    ) {
        var d = store.updateDish(id, categoryId, name, imageUrl, recommendationLevel, description, isAvailable, sortOrder, tags, soldOut);
        if (d == null) throw new BusinessException(BusinessErrorCode.NOT_FOUND, "notfound");
        return ApiPayloads.updated(id);
    }

    public Map<String, Object> deleteDish(long id) {
        var ok = store.deleteDish(id);
        if (!ok) throw new BusinessException(BusinessErrorCode.NOT_FOUND, "notfound");
        return ApiPayloads.deleted(id);
    }
}
