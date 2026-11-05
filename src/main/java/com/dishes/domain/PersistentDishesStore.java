package com.dishes.domain;

import com.dishes.ExtensionSchemeRegistry;
import com.dishes.api.BusinessErrorCode;
import com.dishes.api.BusinessException;
import com.dishes.domain.finder.PersistentDishesFinder;
import com.dishes.domain.service.PersistentDishesDomainService;
import com.dishes.domain.service.DishTags;
import com.dishes.extension.DishCategory;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

@Primary
@Component
public class PersistentDishesStore implements DishesStore {
    private final ReactiveExtensionClient client;
    private final ExtensionSchemeRegistry extensionSchemeRegistry;
    private final PersistentDishesFinder finder;
    private final PersistentDishesDomainService domainService;

    public PersistentDishesStore(ReactiveExtensionClient client, ExtensionSchemeRegistry extensionSchemeRegistry, PersistentDishesFinder finder, PersistentDishesDomainService domainService) {
        this.client = client;
        this.extensionSchemeRegistry = extensionSchemeRegistry;
        this.finder = finder;
        this.domainService = domainService;
    }

    @Override
    public List<Category> listCategories() { return finder.listCategories(); }

    @Override
    public Category createCategory(String name, String slug, int sortOrder) {
        extensionSchemeRegistry.ensureRegistered();
        var n = name == null ? "" : name.trim();
        if (n.isBlank()) throw new BusinessException(BusinessErrorCode.BAD_REQUEST, "分类名称不能为空");
        var normalizedSlug = domainService.normalizeSlug(slug);
        domainService.ensureCategorySlugUnique(normalizedSlug, null, listCategories());
        var id = finder.nextId("category");
        var c = new DishCategory();
        var meta = new Metadata();
        meta.setName(String.valueOf(id));
        c.setMetadata(meta);
        var spec = new DishCategory.Spec();
        spec.setName(n); spec.setSlug(normalizedSlug); spec.setSortOrder(sortOrder);
        c.setSpec(spec);
        if (withIndexRepair(() -> client.create(c).block()) == null) throw new IllegalStateException("create category failed");
        return new Category(id, n, normalizedSlug, sortOrder);
    }

    @Override
    public Category updateCategory(long id, String name, String slug, int sortOrder) {
        extensionSchemeRegistry.ensureRegistered();
        var c = finder.getCategoryExt(id);
        if (c == null) return null;
        var n = name == null ? "" : name.trim();
        if (n.isBlank()) throw new BusinessException(BusinessErrorCode.BAD_REQUEST, "分类名称不能为空");
        var normalizedSlug = domainService.normalizeSlug(slug);
        domainService.ensureCategorySlugUnique(normalizedSlug, id, listCategories());
        c.getSpec().setName(n); c.getSpec().setSlug(normalizedSlug); c.getSpec().setSortOrder(sortOrder);
        if (withIndexRepair(() -> client.update(c).block()) == null) throw new IllegalStateException("update category failed");
        return new Category(id, n, normalizedSlug, sortOrder);
    }

    @Override
    public boolean deleteCategory(long id) {
        extensionSchemeRegistry.ensureRegistered();
        if (listDishes(null).stream().anyMatch(d -> d.categoryId() == id)) return false;
        var c = finder.getCategoryExt(id);
        return c != null && withIndexRepair(() -> client.delete(c).block()) != null;
    }

    @Override
    public List<Dish> listDishes(Long categoryId) { return finder.listDishes(categoryId); }

    @Override
    public Dish createDish(long categoryId, String name, String imageUrl, int recommendationLevel, String description, boolean isAvailable, int sortOrder, List<String> tags, boolean soldOut) {
        var normalizedTags = DishTags.normalize(tags);
        extensionSchemeRegistry.ensureRegistered();
        if (finder.getCategoryExt(categoryId) == null) throw new BusinessException(BusinessErrorCode.BAD_REQUEST, "分类不存在");
        domainService.validateDishInput(name, imageUrl, recommendationLevel);
        var id = finder.nextId("dish");
        var d = new com.dishes.extension.Dish();
        var meta = new Metadata();
        meta.setName(String.valueOf(id));
        d.setMetadata(meta);
        var spec = new com.dishes.extension.Dish.Spec();
        spec.setCategoryId(categoryId); spec.setName(name == null ? "" : name.trim());
        spec.setImageUrl(domainService.trimToNull(imageUrl)); spec.setRecommendationLevel(recommendationLevel);
        spec.setDescription(domainService.trimToNull(description)); spec.setIsAvailable(isAvailable); spec.setSoldOut(soldOut);
        spec.setSortOrder(sortOrder); spec.setTags(normalizedTags); d.setSpec(spec);
        if (withIndexRepair(() -> client.create(d).block()) == null) throw new IllegalStateException("create dish failed");
        var catName = finder.categoryNameMap().getOrDefault(categoryId, "");
        return new Dish(id, categoryId, catName, name == null ? "" : name.trim(), domainService.trimToNull(imageUrl), recommendationLevel, domainService.trimToNull(description), isAvailable, sortOrder, normalizedTags, soldOut);
    }

    @Override
    public Dish updateDish(long id, long categoryId, String name, String imageUrl, int recommendationLevel, String description, boolean isAvailable, int sortOrder, List<String> tags, boolean soldOut) {
        var normalizedTags = DishTags.normalize(tags);
        extensionSchemeRegistry.ensureRegistered();
        if (finder.getCategoryExt(categoryId) == null) throw new BusinessException(BusinessErrorCode.BAD_REQUEST, "分类不存在");
        domainService.validateDishInput(name, imageUrl, recommendationLevel);
        var d = finder.getDishExt(id);
        if (d == null) return null;
        var spec = d.getSpec();
        var effectiveSoldOut = soldOut;
        spec.setCategoryId(categoryId); spec.setName(name == null ? "" : name.trim());
        spec.setImageUrl(domainService.trimToNull(imageUrl)); spec.setRecommendationLevel(recommendationLevel);
        spec.setDescription(domainService.trimToNull(description)); spec.setIsAvailable(isAvailable); spec.setSoldOut(effectiveSoldOut);
        spec.setSortOrder(sortOrder); spec.setTags(normalizedTags);
        if (withIndexRepair(() -> client.update(d).block()) == null) throw new IllegalStateException("update dish failed");
        var catName = finder.categoryNameMap().getOrDefault(categoryId, "");
        return new Dish(id, categoryId, catName, name == null ? "" : name.trim(), domainService.trimToNull(imageUrl), recommendationLevel, domainService.trimToNull(description), isAvailable, sortOrder, normalizedTags, effectiveSoldOut);
    }

    @Override
    public boolean deleteDish(long id) {
        extensionSchemeRegistry.ensureRegistered();
        var d = finder.getDishExt(id);
        return d != null && withIndexRepair(() -> client.delete(d).block()) != null;
    }

    private <T> T withIndexRepair(Supplier<T> action) {
        try {
            return action.get();
        } catch (Throwable ex) {
            if (containsNoIndicesError(ex)) {
                extensionSchemeRegistry.rebuildAll();
                return action.get();
            }
            throw ex;
        }
    }

    private boolean containsNoIndicesError(Throwable throwable) {
        var cursor = throwable;
        while (cursor != null) {
            var msg = cursor.getMessage();
            if (msg != null && msg.contains("No indices found for type")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

}
