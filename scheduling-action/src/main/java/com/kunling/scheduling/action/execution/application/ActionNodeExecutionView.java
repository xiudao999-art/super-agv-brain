package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.execution.domain.ActionNodeState;
import com.kunling.scheduling.action.execution.domain.ExecutionError;

import java.time.Instant;
import java.util.UUID;

public record ActionNodeExecutionView(
        UUID id,
        int ordinal,
        String executionNodeId,
        String sourcePath,
        String capabilityKey,
        String capabilityContractHash,
        ActionNodeState state,
        int attempt,
        String consumeId,
        JsonNode resolvedInput,
        JsonNode output,
        JsonNode evidence,
        ExecutionError error,
        Instant startedAt,
        Instant completedAt) {
}
