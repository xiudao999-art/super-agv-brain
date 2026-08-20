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
        CANCELLED;

        /** 把 cnet8 MainActionState 明确收敛为上游执行证据状态。 */
        public static State fromWireState(String value) {
            if (value == null) throw new IllegalArgumentException("机器人动作状态不能为空。");
            switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
                case "ACCEPTED": return ACCEPTED;
                case "RUNNING": return RUNNING;
                case "FINISHED":
                case "PHYSICAL_DONE": return PHYSICAL_DONE;
                case "ERROR":
                case "BUSY":
                case "FAILED": return FAILED;
                case "HANG":
                case "UNKNOWN": return UNKNOWN;
                case "CANCELLED": return CANCELLED;
                default: throw new IllegalArgumentException("不支持的机器人动作状态：" + value);
            }
        }
    }
}
