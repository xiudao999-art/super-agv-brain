package com.kunling.scheduling.agvflow.enums;

import lombok.Getter;

@Getter
public enum NodeCategoryEnums {

    GENERAL("GENERAL", "通用节点"),
    MAIN("MAIN", "主节点"),
    //子节点
    CHILD_NODE("CHILD_NODE","子节点"),
    OTHER("OTHER", "其他节点"),
    ;

    private final String code;
    private final String label;

    NodeCategoryEnums(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static NodeCategoryEnums fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (NodeCategoryEnums category : values()) {
            if (category.code.equals(code)) {
                return category;
            }
        }
        return null;
    }
}
