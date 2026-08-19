package com.kunling.scheduling.action.robotbridge.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** ACTION_EVENT 与 ACTION_STATUS 的协议无关表示。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RobotActionEvent {
    String messageType;
    String messageId;
    String sessionId;
    String robotId;
    String actionInstanceId;
    String deviceCommandId;
    long sequence;
    State state;
    JsonNode resolvedSteps;
    JsonNode physicalResult;
    JsonNode error;
    Instant timestamp;
    @ConstructorProperties({"messageType", "messageId", "sessionId", "robotId", "actionInstanceId", "deviceCommandId", "sequence", "state", "resolvedSteps", "physicalResult", "error", "timestamp"})
    public RobotActionEvent(
            String messageType,
            String messageId,
            String sessionId,
            String robotId,
            String actionInstanceId,
            String deviceCommandId,
            long sequence,
            State state,
            JsonNode resolvedSteps,
            JsonNode physicalResult,
            JsonNode error,
            Instant timestamp
    ) {
        this.messageType = messageType;
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.robotId = robotId;
        this.actionInstanceId = actionInstanceId;
        this.deviceCommandId = deviceCommandId;
        this.sequence = sequence;
        this.state = state;
        this.resolvedSteps = resolvedSteps;
        this.physicalResult = physicalResult;
        this.error = error;
        this.timestamp = timestamp;
    }

    public enum State {
        ACCEPTED,
        RUNNING,
        PHYSICAL_DONE,
        FAILED,
        UNKNOWN,
        CANCELLED
    }
}
