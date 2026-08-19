package com.kunling.scheduling.action.fixed.domain;

import java.util.Arrays;

/** 一期允许下发给机器人客户端的四类固定主动作。 */
public enum FixedActionType {
    MOVE("MOVE", "move.json"),
    ARM_HOME("ARM.HOME", "arm-home.json"),
    ARM_PICK("ARM.PICK", "arm-pick.json"),
    ARM_PLACE("ARM.PLACE", "arm-place.json");

    private final String wireName;
    private final String resourceName;

    FixedActionType(String wireName, String resourceName) {
        this.wireName = wireName;
        this.resourceName = resourceName;
    }

    public String wireName() {
        return wireName;
    }

    public String resourceName() {
        return resourceName;
    }

    public static FixedActionType fromWireName(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("actionType 不能为空");
        }
        return Arrays.stream(values())
                .filter(type -> type.wireName.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "一期仅支持 MOVE、ARM.HOME、ARM.PICK、ARM.PLACE，实际为: " + value));
    }
}
