package com.kunling.scheduling.action.execution.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.time.Instant;

/** 下游 2.0 执行事件的无损只读视图。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionExecutionEventView {
    String messageId;
    String messageType;
    String sessionId;
    String robotId;
    String actionInstanceId;
    String deviceCommandId;
    long sequence;
    String state;
    JsonNode stepEvent;
    JsonNode resolvedSteps;
    PhysicalOutcome physicalOutcome;
    JsonNode error;
    Instant timestamp;
    Instant receivedAt;

    @ConstructorProperties({"messageId", "messageType", "sessionId", "robotId", "actionInstanceId",
            "deviceCommandId", "sequence", "state", "stepEvent", "resolvedSteps",
            "physicalOutcome", "error", "timestamp", "receivedAt"})
    public ActionExecutionEventView(String messageId, String messageType, String sessionId,
                                    String robotId, String actionInstanceId, String deviceCommandId,
                                    long sequence, String state, JsonNode stepEvent,
                                    JsonNode resolvedSteps, PhysicalOutcome physicalOutcome,
                                    JsonNode error, Instant timestamp, Instant receivedAt) {
        this.messageId = messageId;
        this.messageType = messageType;
        this.sessionId = sessionId;
        this.robotId = robotId;
        this.actionInstanceId = actionInstanceId;
        this.deviceCommandId = deviceCommandId;
        this.sequence = sequence;
        this.state = state;
        this.stepEvent = stepEvent;
        this.resolvedSteps = resolvedSteps;
        this.physicalOutcome = physicalOutcome;
        this.error = error;
        this.timestamp = timestamp;
        this.receivedAt = receivedAt;
    }
}
