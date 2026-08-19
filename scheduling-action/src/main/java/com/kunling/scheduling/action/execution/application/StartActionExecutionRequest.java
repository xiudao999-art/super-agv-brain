package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;

public record StartActionExecutionRequest(
        String actionInstanceId,
        String robotId,
        String actionKey,
        String actionVersion,
        String workflowInstanceId,
        String workflowNodeInstanceId,
        JsonNode input,
        JsonNode context) {
}
