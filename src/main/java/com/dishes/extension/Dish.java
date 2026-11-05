package com.dishes.extension;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
    group = "dishes.plugin.halo.run",
    version = "v1alpha1",
    kind = "Dish",
    plural = "dishes",
    singular = "dish"
)
public class Dish extends AbstractExtension {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "DishSpec")
    public static class Spec {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        private Long categoryId;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 128)
        private String name;

        @Schema(description = "Image URL", maxLength = 512)
        private String imageUrl;

        @Schema(description = "1-5", minimum = "1", maximum = "5", defaultValue = "3")
        private Integer recommendationLevel;

        @Schema(description = "Description", maxLength = 2048)
        private String description;

        @Schema(defaultValue = "true")
        private Boolean isAvailable;

        @Schema(description = "Temporarily sold out while remaining listed", defaultValue = "false")
        private Boolean soldOut;

        @Schema(defaultValue = "0")
        private Integer sortOrder;

        @Schema(description = "LemonSlice free-form tags (up to 16)")
        private List<String> tags;
    }
}


