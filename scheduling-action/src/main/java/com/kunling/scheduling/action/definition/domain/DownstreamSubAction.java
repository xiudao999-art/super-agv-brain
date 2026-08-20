package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** cnet8 当前可以解释执行的 L1 子动作协议清单。 */
public enum DownstreamSubAction {
    MOVE_TO_MAP_POINT("MOVE_TO_MAP_POINT", "pointName"),
    MOVE_TO_POSE("MOVE_TO_POSE", "poseRole"),
    GRIP_OPEN("GRIP.OPEN"),
    GRIP_CLOSE("GRIP.CLOSE"),
    GRIP_VERIFY_LOAD("GRIP.VERIFY_LOAD"),
    VISION_VERIFY_MATERIAL("VISION.VERIFY_MATERIAL"),
    VISION_VERIFY_PLACEMENT("VISION.VERIFY_PLACEMENT"),
    VISION_CAPTURE("VISION.CAPTURE"),
    CHASSIS_VERIFY_STOPPED("CHASSIS_VERIFY_STOPPED"),
    ARM_VERIFY_HOME("ARM_VERIFY_HOME");

    private final String wireName;
    private final Set<String> requiredParameters;

    DownstreamSubAction(String wireName, String... requiredParameters) {
        this.wireName = wireName;
        this.requiredParameters = Collections.unmodifiableSet(
                new LinkedHashSet<String>(Arrays.asList(requiredParameters)));
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    /** cnet8 无默认值、缺失必定拒绝的最小参数集。 */
    public Set<String> requiredParameters() {
        return requiredParameters;
    }

    @JsonCreator
    public static DownstreamSubAction fromWireName(String value) {
        for (DownstreamSubAction action : values()) {
            if (action.wireName.equalsIgnoreCase(value) || action.name().equalsIgnoreCase(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("下游不支持子动作：" + value);
    }
}
