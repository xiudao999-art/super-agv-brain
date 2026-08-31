package com.kunling.scheduling.action.execution.infrastructure;

import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.execution.domain.ActionExecutionEventView;
import com.kunling.scheduling.action.config.JsonCodec;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "action_execution_event")
public class ActionExecutionEventEntity {
    @Id
    @Column(name = "message_id", length = 64, nullable = false)
    private String messageId;
    @Column(name = "action_instance_id", length = 128, nullable = false)
    private String actionInstanceId;
    @Column(name = "robot_id", length = 128, nullable = false)
    private String robotId;
    @Column(name = "message_type", length = 32, nullable = false)
    private String messageType;
    @Column(name = "event_sequence", nullable = false)
    private long eventSequence;
    @Column(name = "event_state", length = 32, nullable = false)
    private String eventState;
    @Lob
    @Column(name = "payload_json", nullable = false, columnDefinition = "longtext")
    private String payloadJson;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected ActionExecutionEventEntity() {
    }

    public ActionExecutionEventEntity(RobotActionEvent event, JsonCodec jsonCodec, Instant receivedAt) {
        this.messageId = event.messageId();
        this.actionInstanceId = event.actionInstanceId();
        this.robotId = event.robotId();
        this.messageType = event.messageType();
        this.eventSequence = event.sequence();
        this.eventState = event.state().name();
        this.payloadJson = jsonCodec.write(event);
        this.receivedAt = receivedAt;
    }

    /** 从无损原始 JSON 构造 API 视图，避免为各厂商证据持续增加数据库列。 */
    public ActionExecutionEventView toView(JsonCodec jsonCodec) {
        com.fasterxml.jackson.databind.JsonNode payload = jsonCodec.readTree(payloadJson);
        return new ActionExecutionEventView(messageId, messageType,
                text(payload, "sessionId"), robotId, actionInstanceId,
                text(payload, "deviceCommandId"), eventSequence, eventState,
                nullable(payload.get("stepEvent")), nullable(payload.get("resolvedSteps")),
                enumValue(payload, "physicalOutcome"), nullable(payload.get("error")),
                instant(payload, "timestamp"), receivedAt);
    }

    private String text(com.fasterxml.jackson.databind.JsonNode payload, String field) {
        com.fasterxml.jackson.databind.JsonNode value = payload.get(field);
        if (value == null || !value.isTextual() || value.textValue().trim().isEmpty()) {
            throw new IllegalStateException("执行事件缺少持久化字段：" + field);
        }
        return value.textValue();
    }

    private com.fasterxml.jackson.databind.JsonNode nullable(com.fasterxml.jackson.databind.JsonNode value) {
        return value == null || value.isNull() ? null : value.deepCopy();
    }

    private com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome enumValue(
            com.fasterxml.jackson.databind.JsonNode payload, String field) {
        com.fasterxml.jackson.databind.JsonNode value = payload.get(field);
        return value == null || value.isNull() ? null
                : com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome.valueOf(value.asText());
    }

    private Instant instant(com.fasterxml.jackson.databind.JsonNode payload, String field) {
        com.fasterxml.jackson.databind.JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalStateException("执行事件缺少持久化字段：" + field);
        }
        if (value.isTextual()) return Instant.parse(value.textValue());
        throw new IllegalStateException("执行事件时间字段必须是 ISO-8601 字符串：" + field);
    }
}
