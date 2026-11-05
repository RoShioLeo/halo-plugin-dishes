package com.dishes.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dishes.ExtensionSchemeRegistry;
import com.dishes.domain.finder.PersistentDishesFinder;
import com.dishes.domain.service.PersistentDishesDomainService;
import com.dishes.extension.Dish;
import com.dishes.extension.DishCategory;
import com.dishes.service.PublicFacadeService;
import com.dishes.service.DishesSettingsService;
import com.dishes.service.admin.AdminMenuService;
import com.dishes.service.publics.PublicAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class DishStockPersistenceTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final ExtensionSchemeRegistry registry = mock(ExtensionSchemeRegistry.class);

    @Test
    void tagsAndIndependentStockStateRemainVisibleInTheCatalogue() {
        var available = dish(false); available.getSpec().setTags(List.of("早餐", "午餐", "清淡"));
        when(client.listAll(eq(Dish.class), any(ListOptions.class), any(Sort.class))).thenReturn(Flux.just(available));
        when(client.listAll(eq(DishCategory.class), any(ListOptions.class), any(Sort.class))).thenReturn(Flux.just(category()));
        var finder = new PersistentDishesFinder(client, registry);
        var first = finder.listDishes(null).getFirst();
        assertFalse(first.soldOut()); assertTrue(first.isAvailable());
        assertEquals(List.of("早餐", "午餐", "清淡"), first.tags());
        available.getSpec().setSoldOut(true);
        assertTrue(finder.listDishes(null).getFirst().soldOut());
    }

    @Test
    void stockWritesRoundtripAndPublicAdminCataloguesKeepSoldOutDishVisible() throws Exception {
        var finder = mock(PersistentDishesFinder.class);
        when(finder.getCategoryExt(1)).thenReturn(category());
        when(finder.nextId("dish")).thenReturn(7L);
        when(finder.categoryNameMap()).thenReturn(Map.of(1L, "家常"));
        when(client.create(any(Dish.class))).thenAnswer(call -> Mono.just((Dish) call.getArgument(0)));
        var store = new PersistentDishesStore(client, registry, finder, new PersistentDishesDomainService());
        var created = store.createDish(1, "测试菜", "/dish.jpg", 5, "描述", true, 0, List.of("午餐"), true);
        var saved = ArgumentCaptor.forClass(Dish.class); verify(client).create(saved.capture());
        assertEquals(true, saved.getValue().getSpec().getSoldOut());
        assertEquals(true, saved.getValue().getSpec().getIsAvailable());
        assertTrue(created.soldOut());
        when(finder.listDishes(null)).thenReturn(List.of(created));
        var admin = new AdminMenuService(store).listDishes();
        var publicCatalogue = new PublicFacadeService(store, mock(PublicAccessService.class)).dishes(null, null);
        assertEquals(1, ((List<?>) admin.get("items")).size());
        assertEquals(1, ((List<?>) publicCatalogue.get("items")).size());
        var json = new ObjectMapper().writeValueAsString(publicCatalogue);
        assertTrue(json.contains("\"soldOut\":true"));
        assertTrue(json.contains("\"isAvailable\":true"));
    }

    @Test
    void explicitStockUpdateRestoresSupplyIndependentlyOfVisibility() {
        var finder = mock(PersistentDishesFinder.class);
        var existing = dish(true);
        when(finder.getCategoryExt(1)).thenReturn(category()); when(finder.getDishExt(7)).thenReturn(existing);
        when(finder.categoryNameMap()).thenReturn(Map.of(1L, "家常"));
        when(client.update(any(Dish.class))).thenAnswer(call -> Mono.just((Dish) call.getArgument(0)));
        var store = new PersistentDishesStore(client, registry, finder, new PersistentDishesDomainService());
        var restored = store.updateDish(7, 1, "已恢复", "/dish.jpg", 5, "新描述", true, 0, List.of("午餐"), false);
        assertFalse(restored.soldOut()); assertEquals(false, existing.getSpec().getSoldOut());
    }

    private static DishCategory category() {
        var category = new DishCategory(); var metadata = new Metadata(); metadata.setName("1"); category.setMetadata(metadata);
        var spec = new DishCategory.Spec(); spec.setName("家常"); spec.setSlug("home"); spec.setSortOrder(0); category.setSpec(spec);
        return category;
    }
    private static Dish dish(Boolean soldOut) {
        var dish = new Dish(); var metadata = new Metadata(); metadata.setName("7"); dish.setMetadata(metadata);
        var spec = new Dish.Spec(); spec.setCategoryId(1L); spec.setName("测试菜"); spec.setIsAvailable(true);
        spec.setRecommendationLevel(5); spec.setSoldOut(soldOut); dish.setSpec(spec);
        return dish;
    }
}
