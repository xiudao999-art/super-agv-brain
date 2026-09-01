package com.kunling.scheduling.app.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/** 页面可直接展示的业务场景或原子操作选项。 */
@Schema(description = "Action 业务场景目录选项")
public final class ActionSceneCatalogOption {
    @Schema(description = "稳定编码", example = "HOME")
    private final String code;
    @Schema(description = "展示名称", example = "回零")
    private final String name;

    public ActionSceneCatalogOption(String code, String name) {
        this.code = Objects.requireNonNull(code, "目录编码不能为空");
        this.name = Objects.requireNonNull(name, "目录名称不能为空");
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
