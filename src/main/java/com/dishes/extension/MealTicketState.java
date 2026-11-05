package com.dishes.extension;

import com.dishes.service.kitchen.KitchenModels.SubmittedBatch;
import com.dishes.service.kitchen.MealSource;
import com.dishes.service.kitchen.MealMenu;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

/** Atomic meal aggregate: source quantities, submitted batches and published menu. Modified 2026-11-05, GPL-3.0. */
@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "dishes.plugin.halo.run", version = "v1alpha1", kind = "MealTicketState",
    plural = "mealticketstates", singular = "mealticketstate")
public class MealTicketState extends AbstractExtension {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "MealTicketStateSpec")
    public static class Spec {
        private String date;
        private String period;
        private String activeBatchId;
        private long revision;
        private List<SubmittedBatch> submittedBatches;
        private List<MealSource> sources;
        private MealMenu.Spec confirmedMenu;
    }
}
