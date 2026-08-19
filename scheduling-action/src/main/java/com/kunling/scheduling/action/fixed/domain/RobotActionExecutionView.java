package com.kunling.scheduling.action.fixed.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RobotActionExecutionView {
    String actionInstanceId;
    String robotId;
    String deviceCommandId;
    String actionType;
    String actionVersion;
    String templateVersion;
    String requestHash;
    String packageHash;
    RobotActionExecutionState state;
    boolean physicalResultKnown;
    String workflowInstanceId;
    String workflowNodeInstanceId;
    JsonNode commandInput;
    JsonNode resolvedSteps;
    JsonNode physicalResult;
    JsonNode error;
    Instant createdAt;
    Instant updatedAt;
    Instant completedAt;
    @ConstructorProperties({"actionInstanceId", "robotId", "deviceCommandId", "actionType", "actionVersion", "templateVersion", "requestHash", "packageHash", "state", "physicalResultKnown", "workflowInstanceId", "workflowNodeInstanceId", "commandInput", "resolvedSteps", "physicalResult", "error", "createdAt", "updatedAt", "completedAt"})
    public RobotActionExecutionView(
            String actionInstanceId,
            String robotId,
            String deviceCommandId,
            String actionType,
            String actionVersion,
            String templateVersion,
            String requestHash,
            String packageHash,
            RobotActionExecutionState state,
            boolean physicalResultKnown,
            String workflowInstanceId,
            String workflowNodeInstanceId,
            JsonNode commandInput,
            JsonNode resolvedSteps,
            JsonNode physicalResult,
            JsonNode error,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt
    ) {
        this.actionInstanceId = actionInstanceId;
        this.robotId = robotId;
        this.deviceCommandId = deviceCommandId;
        this.actionType = actionType;
        this.actionVersion = actionVersion;
        this.templateVersion = templateVersion;
        this.requestHash = requestHash;
        this.packageHash = packageHash;
        this.state = state;
        this.physicalResultKnown = physicalResultKnown;
        this.workflowInstanceId = workflowInstanceId;
        this.workflowNodeInstanceId = workflowNodeInstanceId;
        this.commandInput = commandInput;
        this.resolvedSteps = resolvedSteps;
        this.physicalResult = physicalResult;
        this.error = error;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
    }

}
