package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class StartActionExecutionRequest {
    String actionInstanceId;
    String robotId;
    String actionKey;
    String actionVersion;
    String workflowInstanceId;
    String workflowNodeInstanceId;
    JsonNode input;
    JsonNode context;
    @ConstructorProperties({"actionInstanceId", "robotId", "actionKey", "actionVersion", "workflowInstanceId", "workflowNodeInstanceId", "input", "context"})
    public StartActionExecutionRequest(
            String actionInstanceId,
            String robotId,
            String actionKey,
            String actionVersion,
            String workflowInstanceId,
            String workflowNodeInstanceId,
            JsonNode input,
            JsonNode context
    ) {
        this.actionInstanceId = actionInstanceId;
        this.robotId = robotId;
        this.actionKey = actionKey;
        this.actionVersion = actionVersion;
        this.workflowInstanceId = workflowInstanceId;
        this.workflowNodeInstanceId = workflowNodeInstanceId;
        this.input = input;
        this.context = context;
    }

}
