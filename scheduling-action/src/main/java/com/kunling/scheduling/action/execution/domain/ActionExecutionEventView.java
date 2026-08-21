package com.kunling.scheduling.action.execution.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.time.Instant;

/**
 * 下游执行事件的只读视图。
 *
 * <p>该视图保存下游事实，不负责解释状态机业务语义；原始证据字段保持 JSON，
 * 以便后续兼容不同设备和厂商返回的扩展信息。</p>
 */
@Schema(description = "下游推送的动作执行事件")
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
    JsonNode phaseEvent;
    JsonNode reportState;
    JsonNode resolvedSteps;
    JsonNode physicalResult;
    JsonNode error;
    Instant timestamp;
    Instant receivedAt;

    @ConstructorProperties({"messageId", "messageType", "sessionId", "robotId", "actionInstanceId",
            "deviceCommandId", "sequence", "state", "phaseEvent", "reportState", "resolvedSteps",
            "physicalResult", "error", "timestamp", "receivedAt"})
    public ActionExecutionEventView(String messageId, String messageType, String sessionId,
                                    String robotId, String actionInstanceId, String deviceCommandId,
                                    long sequence, String state, JsonNode phaseEvent, JsonNode reportState,
                                    JsonNode resolvedSteps, JsonNode physicalResult, JsonNode error,
                                    Instant timestamp, Instant receivedAt) {
        this.messageId = messageId;
        this.messageType = messageType;
        this.sessionId = sessionId;
        this.robotId = robotId;
        this.actionInstanceId = actionInstanceId;
        this.deviceCommandId = deviceCommandId;
        this.sequence = sequence;
        this.state = state;
        this.phaseEvent = phaseEvent;
        this.reportState = reportState;
        this.resolvedSteps = resolvedSteps;
        this.physicalResult = physicalResult;
        this.error = error;
        this.timestamp = timestamp;
        this.receivedAt = receivedAt;
    }
}
