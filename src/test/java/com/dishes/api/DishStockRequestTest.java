package com.dishes.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dishes.service.AdminFacadeService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class DishStockRequestTest {
    private final AdminFacadeService facade = mock(AdminFacadeService.class);
    private final WebTestClient client = WebTestClient.bindToController(new AdminApiController(facade)).build();

    @Test
    void createMapsExplicitStockAndTags() {
        when(facade.createDish(anyLong(), any(), any(), anyInt(), any(), anyBoolean(), anyInt(), any(), anyBoolean()))
            .thenReturn(Map.of("id", 7));
        var body = dishBody(); body.put("soldOut", true);
        client.post().uri("/apis/plugins/dishes/admin/dishes").bodyValue(body).exchange().expectStatus().isOk();
        body.put("soldOut", false);
        client.post().uri("/apis/plugins/dishes/admin/dishes").bodyValue(body).exchange().expectStatus().isOk();
        verify(facade).createDish(eq(1L), eq("测试菜"), eq(""), eq(5), eq(""), eq(true), eq(0), eq(List.of("午餐")), eq(true));
        verify(facade).createDish(eq(1L), eq("测试菜"), eq(""), eq(5), eq(""), eq(true), eq(0), eq(List.of("午餐")), eq(false));
    }

    @Test
    void updatePassesCompleteIndependentAvailabilityAndStockFields() {
        when(facade.updateDish(anyLong(), anyLong(), any(), any(), anyInt(), any(), anyBoolean(), anyInt(), any(), anyBoolean()))
            .thenReturn(Map.of("updated", true));
        var body = dishBody(); body.put("isAvailable", false); body.put("soldOut", true);
        client.put().uri("/apis/plugins/dishes/admin/dishes/7").bodyValue(body).exchange().expectStatus().isOk();
        verify(facade).updateDish(eq(7L), eq(1L), eq("测试菜"), eq(""), eq(5), eq(""), eq(false), eq(0), eq(List.of("午餐")), eq(true));
    }

    private Map<String, Object> dishBody() {
        var body = new HashMap<String, Object>(); body.put("categoryId", 1); body.put("name", "测试菜");
        body.put("imageUrl", ""); body.put("recommendationLevel", 5); body.put("description", "");
        body.put("isAvailable", true); body.put("soldOut", false); body.put("sortOrder", 0); body.put("tags", List.of("午餐"));
        return body;
    }
}
