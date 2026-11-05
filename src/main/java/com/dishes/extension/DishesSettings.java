package com.dishes.extension;

import io.swagger.v3.oas.annotations.media.Schema;
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
    kind = "DishesSettings",
    plural = "dishessettings",
    singular = "dishesettings"
)
public class DishesSettings extends AbstractExtension {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    @Data
    @Schema(name = "DishesSettingsSpec")
    public static class Spec {
        /** none | password */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"none", "password"})
        private String accessMode;

        @Schema(description = "前台访问密码（accessMode=password 时生效）")
        private String accessPassword;

        @Schema(description = "自定义前台访问链接（可选）")
        private String publicAccessUrl;

        @Schema(description = "前台 Logo（附件库图片 URL，可选）")
        private String publicLogoUrl;

        @Schema(description = "前台页面标题（浏览器标签，可选，留空用默认）")
        private String publicSiteTitle;

        @Schema(description = "前台顶栏主标题（可选，留空用默认）")
        private String publicBrandTitle;

        @Schema(description = "前台顶栏副标题（可选，留空用默认）")
        private String publicBrandSubtitle;

        @Schema(description = "前台域名白名单（逗号或换行分隔，留空表示不限制）")
        private String publicDomainWhitelist;

    }
}

