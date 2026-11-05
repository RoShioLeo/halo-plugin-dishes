package com.dishes.service;

import com.dishes.service.admin.AdminMenuService;
import com.dishes.service.admin.AdminSettingsService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminFacadeService {

    private final AdminMenuService adminMenuService;
    private final AdminSettingsService adminSettingsService;

    public AdminFacadeService(
        AdminMenuService adminMenuService,
        AdminSettingsService adminSettingsService
    ) {
        this.adminMenuService = adminMenuService;
        this.adminSettingsService = adminSettingsService;
    }

    public Map<String, Object> listCategories() {
        return adminMenuService.listCategories();
    }

    public Map<String, Object> createCategory(String name, String slug, int sortOrder) {
        return adminMenuService.createCategory(name, slug, sortOrder);
    }

    public Map<String, Object> updateCategory(long id, String name, String slug, int sortOrder) {
        return adminMenuService.updateCategory(id, name, slug, sortOrder);
    }

    public Map<String, Object> deleteCategory(long id) {
        return adminMenuService.deleteCategory(id);
    }

    public Map<String, Object> listDishes() {
        return adminMenuService.listDishes();
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
        return adminMenuService.createDish(
            categoryId,
            name,
            imageUrl,
            recommendationLevel,
            description,
            isAvailable,
            sortOrder,
            tags,
            soldOut
        );
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
        return adminMenuService.updateDish(
            id,
            categoryId,
            name,
            imageUrl,
            recommendationLevel,
            description,
            isAvailable,
            sortOrder,
            tags,
            soldOut
        );
    }

    public Map<String, Object> deleteDish(long id) {
        return adminMenuService.deleteDish(id);
    }

    public Map<String, Object> getSettings() {
        return adminSettingsService.getSettings();
    }

    public Map<String, Object> updateSettings(
        String accessMode,
        String accessPassword,
        String publicAccessUrl,
        String publicLogoUrl,
        String publicSiteTitle,
        String publicBrandTitle,
        String publicBrandSubtitle,
        String publicDomainWhitelist
    ) {
        return adminSettingsService.updateSettings(
            accessMode,
            accessPassword,
            publicAccessUrl,
            publicLogoUrl,
            publicSiteTitle,
            publicBrandTitle,
            publicBrandSubtitle,
            publicDomainWhitelist
        );
    }
}

