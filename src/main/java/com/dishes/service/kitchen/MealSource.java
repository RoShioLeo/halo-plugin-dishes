package com.dishes.service.kitchen;

import com.dishes.service.kitchen.KitchenModels.MenuItem;
import com.dishes.service.kitchen.KitchenModels.Selection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** Private participant snapshot stored only inside its meal aggregate. */
@Data
public class MealSource {
    private String id;
    private Spec spec;

    @Data
    @Schema(name = "MealSourceSnapshot")
    public static class Spec {
        private String date;
        private String period;
        private String batchId;
        private String ownerKey;
        private String username;
        private boolean guest;
        private String participantId;
        private String displayName;
        private String avatarUrl;
        private long revision;
        private List<Selection> items;
        private List<MenuItem> customItems;
        private String createdAt;
        private String offMenu;
        private String remark;
        private String updatedAt;
        private String lastEditedBy;
        private String lastEditedAt;
        private String lastEditedByUsername;
    }
}
