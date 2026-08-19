package com.kunling.scheduling.action.fixed.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** 创建执行记录所需的不可变快照，包含已物化动作包而不是模板引用。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class NewRobotActionExecution {
    String actionInstanceId;
    String robotId;
    String deviceCommandId;
    FixedActionType actionType;
    String actionVersion;
    String templateVersion;
    String requestHash;
    String packageHash;
    String workflowInstanceId;
    String workflowNodeInstanceId;
    JsonNode requestInput;
    JsonNode commandInput;
    int timeoutMs;
    Instant createdAt;
    @ConstructorProperties({"actionInstanceId", "robotId", "deviceCommandId", "actionType", "actionVersion", "templateVersion", "requestHash", "packageHash", "workflowInstanceId", "workflowNodeInstanceId", "requestInput", "commandInput", "timeoutMs", "createdAt"})
    public NewRobotActionExecution(
            String actionInstanceId,
            String robotId,
            String deviceCommandId,
            FixedActionType actionType,
            String actionVersion,
            String templateVersion,
            String requestHash,
            String packageHash,
            String workflowInstanceId,
            String workflowNodeInstanceId,
            JsonNode requestInput,
            JsonNode commandInput,
            int timeoutMs,
            Instant createdAt
    ) {
        this.actionInstanceId = actionInstanceId;
        this.robotId = robotId;
        this.deviceCommandId = deviceCommandId;
        this.actionType = actionType;
        this.actionVersion = actionVersion;
        this.templateVersion = templateVersion;
        this.requestHash = requestHash;
        this.packageHash = packageHash;
        this.workflowInstanceId = workflowInstanceId;
        this.workflowNodeInstanceId = workflowNodeInstanceId;
        this.requestInput = requestInput;
        this.commandInput = commandInput;
        this.timeoutMs = timeoutMs;
        this.createdAt = createdAt;
    }

}
