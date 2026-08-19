package com.kunling.scheduling.action.robotbridge.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** 与传输方式无关的完整动作包下发命令。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RobotActionCommand {
    String robotId;
    String actionInstanceId;
    String deviceCommandId;
    String workflowInstanceId;
    String nodeInstanceId;
    String actionVersion;
    String packageHash;
    JsonNode input;
    int timeoutMs;
    Instant timestamp;
    @ConstructorProperties({"robotId", "actionInstanceId", "deviceCommandId", "workflowInstanceId", "nodeInstanceId", "actionVersion", "packageHash", "input", "timeoutMs", "timestamp"})
    public RobotActionCommand(
            String robotId,
            String actionInstanceId,
            String deviceCommandId,
            String workflowInstanceId,
            String nodeInstanceId,
            String actionVersion,
            String packageHash,
            JsonNode input,
            int timeoutMs,
            Instant timestamp
    ) {
        this.robotId = robotId;
        this.actionInstanceId = actionInstanceId;
        this.deviceCommandId = deviceCommandId;
        this.workflowInstanceId = workflowInstanceId;
        this.nodeInstanceId = nodeInstanceId;
        this.actionVersion = actionVersion;
        this.packageHash = packageHash;
        this.input = input;
        this.timeoutMs = timeoutMs;
        this.timestamp = timestamp;
    }

}
