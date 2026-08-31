package com.kunling.scheduling.action.robotbridge.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.time.Instant;

/** ACTION_EVENT 在线协议数据结构。 */
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
    JsonNode stepEvent;
    JsonNode resolvedSteps;
    PhysicalOutcome physicalOutcome;
    JsonNode error;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant timestamp;

    @ConstructorProperties({"messageType", "messageId", "sessionId", "robotId", "actionInstanceId",
            "deviceCommandId", "sequence", "state", "stepEvent", "resolvedSteps",
            "physicalOutcome", "error", "timestamp"})
    public RobotActionEvent(String messageType,
                            String messageId,
                            String sessionId,
                            String robotId,
                            String actionInstanceId,
                            String deviceCommandId,
                            long sequence,
                            State state,
                            JsonNode stepEvent,
                            JsonNode resolvedSteps,
                            PhysicalOutcome physicalOutcome,
                            JsonNode error,
                            Instant timestamp) {
        this.messageType = messageType;
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.robotId = robotId;
        this.actionInstanceId = actionInstanceId;
        this.deviceCommandId = deviceCommandId;
        this.sequence = sequence;
        this.state = state;
        this.stepEvent = stepEvent == null ? null : stepEvent.deepCopy();
        this.resolvedSteps = resolvedSteps == null ? null : resolvedSteps.deepCopy();
        this.physicalOutcome = physicalOutcome;
        this.error = error == null ? null : error.deepCopy();
        this.timestamp = timestamp;
    }

    public enum State {
        ACCEPTED,
        RUNNING,
        FINISHED,
        REJECTED,
        FAILED,
        UNKNOWN;

        public static State fromWireState(String value) {
            if (value == null) throw new IllegalArgumentException("机器人动作状态不能为空。");
            try {
                return valueOf(value);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("不支持的机器人动作状态：" + value, exception);
            }
        }
    }
}
