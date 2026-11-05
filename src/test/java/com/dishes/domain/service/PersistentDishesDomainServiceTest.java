package com.dishes.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dishes.api.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersistentDishesDomainServiceTest {

    private final PersistentDishesDomainService service = new PersistentDishesDomainService();

    @Test
    void validateDishInputShouldRejectEmptyName() {
        var ex = assertThrows(BusinessException.class, () -> service.validateDishInput("  ", "/a.png", 3));
        assertEquals("菜品名称不能为空", ex.getMessage());
    }

    @Test
    void ensureCategorySlugUniqueShouldRejectBlankSlug() {
        var ex = assertThrows(BusinessException.class, () -> service.ensureCategorySlugUnique(" ", null, List.of()));
        assertEquals("slug 不能为空", ex.getMessage());
    }
}
