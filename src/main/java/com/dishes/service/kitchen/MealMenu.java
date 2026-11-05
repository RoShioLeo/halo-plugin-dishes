package com.dishes.service.kitchen;

import com.dishes.service.kitchen.KitchenModels.MenuItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** Chef-published menu, stored atomically with its source quantities and batch lock. */
@Data
public class MealMenu {
    private Spec spec;

    @Data
    @Schema(name = "MealMenuSnapshot")
    public static class Spec {
        private String date;
        private String period;
        private long revision;
        private List<MenuItem> items;
        private String note;
        private Map<String, Long> contributionVersions;
        private String confirmedAt;
        private String confirmedBy;
        private String confirmedByUsername;
    }
}
