package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.execution.domain.ActionNodeState;
import com.kunling.scheduling.action.execution.domain.ExecutionError;

import java.time.Instant;
import java.util.UUID;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionNodeExecutionView {
    UUID id;
    int ordinal;
    String executionNodeId;
    String sourcePath;
    String capabilityKey;
    String capabilityContractHash;
    ActionNodeState state;
    int attempt;
    String consumeId;
    JsonNode resolvedInput;
    JsonNode output;
    JsonNode evidence;
    ExecutionError error;
    Instant startedAt;
    Instant completedAt;
    @ConstructorProperties({"id", "ordinal", "executionNodeId", "sourcePath", "capabilityKey", "capabilityContractHash", "state", "attempt", "consumeId", "resolvedInput", "output", "evidence", "error", "startedAt", "completedAt"})
    public ActionNodeExecutionView(
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
            Instant completedAt
    ) {
        this.id = id;
        this.ordinal = ordinal;
        this.executionNodeId = executionNodeId;
        this.sourcePath = sourcePath;
        this.capabilityKey = capabilityKey;
        this.capabilityContractHash = capabilityContractHash;
        this.state = state;
        this.attempt = attempt;
        this.consumeId = consumeId;
        this.resolvedInput = resolvedInput;
        this.output = output;
        this.evidence = evidence;
        this.error = error;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

}
