package com.kunling.scheduling.action.robotbridge.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 下游会话声明的一个原子操作能力。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RobotOperationCapability {
    String operation;
    int minTimeoutMs;
    int maxTimeoutMs;

    @ConstructorProperties({"operation", "minTimeoutMs", "maxTimeoutMs"})
    public RobotOperationCapability(String operation, int minTimeoutMs, int maxTimeoutMs) {
        this.operation = operation;
        this.minTimeoutMs = minTimeoutMs;
        this.maxTimeoutMs = maxTimeoutMs;
    }
}
