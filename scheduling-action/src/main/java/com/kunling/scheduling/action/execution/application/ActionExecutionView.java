package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.execution.domain.ExecutionError;

import java.time.Instant;
import java.util.List;

public record ActionExecutionView(
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
        Instant completedAt) {
}
