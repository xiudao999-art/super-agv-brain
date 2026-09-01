package com.kunling.scheduling.app.domain;

import java.util.Objects;

/** action_scene_catalog_item 的只读领域实体，不承载任何写库行为。 */
public final class ActionSceneCatalogItem {
    private final String itemType;
    private final String sceneCode;
    private final String itemCode;
    private final String displayName;
    private final int sortOrder;
    private final boolean enabled;

    public ActionSceneCatalogItem(String itemType,
                                  String sceneCode,
                                  String itemCode,
                                  String displayName,
                                  int sortOrder,
                                  boolean enabled) {
        this.itemType = Objects.requireNonNull(itemType, "目录项类型不能为空");
        this.sceneCode = Objects.requireNonNull(sceneCode, "业务场景编码不能为空");
        this.itemCode = Objects.requireNonNull(itemCode, "目录项编码不能为空");
        this.displayName = Objects.requireNonNull(displayName, "目录项名称不能为空");
        this.sortOrder = sortOrder;
        this.enabled = enabled;
    }

    public String getItemType() {
        return itemType;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
