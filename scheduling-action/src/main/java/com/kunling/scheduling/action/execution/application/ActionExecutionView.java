package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.execution.domain.ExecutionError;

import java.time.Instant;
import java.util.List;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionExecutionView {
    String actionInstanceId;
    String robotId;
    String actionKey;
    String actionVersion;
    String workflowInstanceId;
    String workflowNodeInstanceId;
    String planHash;
    ActionExecutionState state;
    boolean physicalResultKnown;
    String currentNodeId;
    JsonNode input;
    JsonNode context;
    JsonNode result;
    ExecutionError error;
    boolean cancelRequested;
    List<ActionNodeExecutionView> resolvedSteps;
    Instant createdAt;
    Instant updatedAt;
    Instant completedAt;
    @ConstructorProperties({"actionInstanceId", "robotId", "actionKey", "actionVersion", "workflowInstanceId", "workflowNodeInstanceId", "planHash", "state", "physicalResultKnown", "currentNodeId", "input", "context", "result", "error", "cancelRequested", "resolvedSteps", "createdAt", "updatedAt", "completedAt"})
    public ActionExecutionView(
            String actionInstanceId,
            String robotId,
            String actionKey,
            String actionVersion,
            String workflowInstanceId,
            String workflowNodeInstanceId,
            String planHash,
            ActionExecutionState state,
            boolean physicalResultKnown,
            String currentNodeId,
            JsonNode input,
            JsonNode context,
            JsonNode result,
            ExecutionError error,
            boolean cancelRequested,
            List<ActionNodeExecutionView> resolvedSteps,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt
    ) {
        this.actionInstanceId = actionInstanceId;
        this.robotId = robotId;
        this.actionKey = actionKey;
        this.actionVersion = actionVersion;
        this.workflowInstanceId = workflowInstanceId;
        this.workflowNodeInstanceId = workflowNodeInstanceId;
        this.planHash = planHash;
        this.state = state;
        this.physicalResultKnown = physicalResultKnown;
        this.currentNodeId = currentNodeId;
        this.input = input;
        this.context = context;
        this.result = result;
        this.error = error;
        this.cancelRequested = cancelRequested;
        this.resolvedSteps = resolvedSteps;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
    }

}
