package com.kunling.scheduling.action.execution.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.time.Instant;

/** Action 执行事实详情；只保留实际命令证据，不携带上游流程上下文。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionExecutionView {
    String actionInstanceId;
    String actionDefinitionId;
    String robotId;
    String deviceCommandId;
    String protocolVersion;
    String requestHash;
    String packageHash;
    ActionExecutionState state;
    PhysicalOutcome physicalOutcome;
    int timeoutMs;
    JsonNode commandInput;
    JsonNode lastStepEvent;
    JsonNode resolvedSteps;
    JsonNode error;
    String dispatchSessionId;
    String dispatchMessageId;
    String lastEventMessageId;
    String lastEventSessionId;
    Long lastEventSequence;
    Instant createdAt;
    Instant updatedAt;
    Instant completedAt;

    @ConstructorProperties({"actionInstanceId", "actionDefinitionId", "robotId", "deviceCommandId",
            "protocolVersion", "requestHash", "packageHash", "state", "physicalOutcome", "timeoutMs",
            "commandInput", "lastStepEvent", "resolvedSteps", "error", "dispatchSessionId",
            "dispatchMessageId", "lastEventMessageId", "lastEventSessionId", "lastEventSequence",
            "createdAt", "updatedAt", "completedAt"})
    public ActionExecutionView(String actionInstanceId, String actionDefinitionId,
                               String robotId, String deviceCommandId, String protocolVersion,
                               String requestHash, String packageHash, ActionExecutionState state,
                               PhysicalOutcome physicalOutcome, int timeoutMs, JsonNode commandInput,
                               JsonNode lastStepEvent, JsonNode resolvedSteps, JsonNode error,
                               String dispatchSessionId, String dispatchMessageId,
                               String lastEventMessageId, String lastEventSessionId,
                               Long lastEventSequence, Instant createdAt, Instant updatedAt,
                               Instant completedAt) {
        this.actionInstanceId = actionInstanceId;
        this.actionDefinitionId = actionDefinitionId;
        this.robotId = robotId;
        this.deviceCommandId = deviceCommandId;
        this.protocolVersion = protocolVersion;
        this.requestHash = requestHash;
        this.packageHash = packageHash;
        this.state = state;
        this.physicalOutcome = physicalOutcome;
        this.timeoutMs = timeoutMs;
        this.commandInput = copy(commandInput);
        this.lastStepEvent = copy(lastStepEvent);
        this.resolvedSteps = copy(resolvedSteps);
        this.error = copy(error);
        this.dispatchSessionId = dispatchSessionId;
        this.dispatchMessageId = dispatchMessageId;
        this.lastEventMessageId = lastEventMessageId;
        this.lastEventSessionId = lastEventSessionId;
        this.lastEventSequence = lastEventSequence;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
    }

    private static JsonNode copy(JsonNode value) {
        return value == null ? null : value.deepCopy();
    }
}
