package com.dishes.domain;

import java.util.List;

public interface DishesStore {
    record Category(long id, String name, String slug, int sortOrder) {}
    record Dish(long id, long categoryId, String categoryName, String name, String imageUrl,
                int recommendationLevel, String description, boolean isAvailable, int sortOrder,
                List<String> tags, boolean soldOut) {}

    List<Category> listCategories();
    Category createCategory(String name, String slug, int sortOrder);
    Category updateCategory(long id, String name, String slug, int sortOrder);
    boolean deleteCategory(long id);

    List<Dish> listDishes(Long categoryId);
    Dish createDish(long categoryId, String name, String imageUrl, int recommendationLevel,
                    String description, boolean isAvailable, int sortOrder, List<String> tags,
                    boolean soldOut);
    Dish updateDish(long id, long categoryId, String name, String imageUrl, int recommendationLevel,
                    String description, boolean isAvailable, int sortOrder, List<String> tags,
                    boolean soldOut);
    boolean deleteDish(long id);
}
