package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** cnet8 当前注册的七种 L2 主动作协议类型。 */
public enum DownstreamActionType {
    MOVE("MOVE", DownstreamSubAction.MOVE_TO_MAP_POINT, DownstreamSubAction.CHASSIS_VERIFY_STOPPED),
    ARM_HOME("ARM.HOME", DownstreamSubAction.MOVE_TO_POSE, DownstreamSubAction.CHASSIS_VERIFY_STOPPED,
            DownstreamSubAction.ARM_VERIFY_HOME),
    ARM_PICK("ARM.PICK", armActions()),
    ARM_PLACE("ARM.PLACE", armActions()),
    ARM_PICK_BATCH("ARM.PICK_BATCH", armActions()),
    ARM_PLACE_BATCH("ARM.PLACE_BATCH", armActions()),
    VISION_CAPTURE("VISION.CAPTURE", DownstreamSubAction.VISION_CAPTURE);

    private final String wireName;
    private final Set<DownstreamSubAction> allowedSubActions;

    DownstreamActionType(String wireName, DownstreamSubAction... allowedSubActions) {
        this.wireName = wireName;
        this.allowedSubActions = Collections.unmodifiableSet(
                allowedSubActions.length == 0
                        ? EnumSet.noneOf(DownstreamSubAction.class)
                        : EnumSet.copyOf(Arrays.asList(allowedSubActions)));
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    public boolean supports(DownstreamSubAction subAction) {
        return subAction != null && allowedSubActions.contains(subAction);
    }

    public Set<DownstreamSubAction> allowedSubActions() {
        return allowedSubActions;
    }

    @JsonCreator
    public static DownstreamActionType fromWireName(String value) {
        for (DownstreamActionType type : values()) {
            if (type.wireName.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("下游不支持主动作类型：" + value);
    }

    private static DownstreamSubAction[] armActions() {
        return new DownstreamSubAction[]{
                DownstreamSubAction.MOVE_TO_POSE,
                DownstreamSubAction.GRIP_OPEN,
                DownstreamSubAction.GRIP_CLOSE,
                DownstreamSubAction.GRIP_VERIFY_LOAD,
                DownstreamSubAction.VISION_VERIFY_MATERIAL,
                DownstreamSubAction.VISION_VERIFY_PLACEMENT,
                DownstreamSubAction.VISION_CAPTURE,
                DownstreamSubAction.CHASSIS_VERIFY_STOPPED,
                DownstreamSubAction.ARM_VERIFY_HOME
        };
    }
}
