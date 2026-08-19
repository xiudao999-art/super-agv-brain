package com.kunling.scheduling.action.fixed.infrastructure;

import com.kunling.scheduling.action.shared.ImmutableCollections;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.fixed.domain.NewRobotActionExecution;
import com.kunling.scheduling.action.fixed.domain.RobotActionExecutionState;
import com.kunling.scheduling.action.fixed.domain.RobotActionExecutionView;
import com.kunling.scheduling.action.shared.JsonCodec;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Version;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "robot_action_execution")
public class RobotActionExecutionEntity {

    @Id
    @Column(name = "action_instance_id", length = 128, nullable = false)
    private String actionInstanceId;

    @Column(name = "robot_id", length = 128, nullable = false)
    private String robotId;

    @Column(name = "device_command_id", length = 128, nullable = false, unique = true)
    private String deviceCommandId;

    @Column(name = "action_type", length = 64, nullable = false)
    private String actionType;

    @Column(name = "action_version", length = 32, nullable = false)
    private String actionVersion;

    @Column(name = "template_version", length = 32, nullable = false)
    private String templateVersion;

    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    @Column(name = "package_hash", length = 64, nullable = false)
    private String packageHash;

    @Column(name = "workflow_instance_id", length = 128)
    private String workflowInstanceId;

    @Column(name = "workflow_node_instance_id", length = 128)
    private String workflowNodeInstanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 32, nullable = false)
    private RobotActionExecutionState state;

    @Column(name = "physical_result_known", nullable = false)
    private boolean physicalResultKnown;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Lob
    @Column(name = "request_input_json", nullable = false, columnDefinition = "longtext")
    private String requestInputJson;

    @Lob
    @Column(name = "command_input_json", nullable = false, columnDefinition = "longtext")
    private String commandInputJson;

    @Lob
    @Column(name = "resolved_steps_json", columnDefinition = "longtext")
    private String resolvedStepsJson;

    @Lob
    @Column(name = "physical_result_json", columnDefinition = "longtext")
    private String physicalResultJson;

    @Lob
    @Column(name = "error_json", columnDefinition = "longtext")
    private String errorJson;

    @Column(name = "dispatch_session_id", length = 64)
    private String dispatchSessionId;

    @Column(name = "dispatch_message_id", length = 64)
    private String dispatchMessageId;

    @Column(name = "last_event_message_id", length = 64)
    private String lastEventMessageId;

    @Column(name = "last_event_sequence")
    private Long lastEventSequence;

    @Column(name = "last_event_session_id", length = 64)
    private String lastEventSessionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected RobotActionExecutionEntity() {
    }

    public RobotActionExecutionEntity(NewRobotActionExecution execution, JsonCodec jsonCodec) {
        this.actionInstanceId = execution.actionInstanceId();
        this.robotId = execution.robotId();
        this.deviceCommandId = execution.deviceCommandId();
        this.actionType = execution.actionType().wireName();
        this.actionVersion = execution.actionVersion();
        this.templateVersion = execution.templateVersion();
        this.requestHash = execution.requestHash();
        this.packageHash = execution.packageHash();
        this.workflowInstanceId = execution.workflowInstanceId();
        this.workflowNodeInstanceId = execution.workflowNodeInstanceId();
        this.state = RobotActionExecutionState.DISPATCH_PENDING;
        this.physicalResultKnown = true;
        this.timeoutMs = execution.timeoutMs();
        this.requestInputJson = jsonCodec.write(execution.requestInput());
        this.commandInputJson = jsonCodec.write(execution.commandInput());
        this.createdAt = execution.createdAt();
        this.updatedAt = execution.createdAt();
    }

    public void markDispatched(String sessionId, String messageId, Instant sentAt) {
        this.dispatchSessionId = sessionId;
        this.dispatchMessageId = messageId;
        if (state == RobotActionExecutionState.DISPATCH_PENDING) {
            state = RobotActionExecutionState.DISPATCHED;
            physicalResultKnown = false;
        }
        updatedAt = sentAt;
    }

    public void hold(String code, String message, JsonCodec jsonCodec, Instant now) {
        if (state.terminal() && state != RobotActionExecutionState.UNKNOWN_HOLD) {
            return;
        }
        state = RobotActionExecutionState.UNKNOWN_HOLD;
        physicalResultKnown = false;
        errorJson = jsonCodec.write(ImmutableCollections.mapOf("code", code, "message", message));
        updatedAt = now;
        completedAt = now;
    }

    public void applyEvent(RobotActionEvent event, JsonCodec jsonCodec, Instant now) {
        validateIdentity(event);
        if (event.sessionId().equals(lastEventSessionId)
                && lastEventSequence != null && event.sequence() <= lastEventSequence) {
            return;
        }
        lastEventSequence = event.sequence();
        lastEventSessionId = event.sessionId();
        lastEventMessageId = event.messageId();
        if (event.resolvedSteps() != null) {
            resolvedStepsJson = jsonCodec.write(event.resolvedSteps());
        }
        if (event.physicalResult() != null) {
            physicalResultJson = jsonCodec.write(event.physicalResult());
        }
        if (event.error() != null) {
            errorJson = jsonCodec.write(event.error());
        }
        updatedAt = now;

        // HOLD 是人工处置边界；迟到的状态只补充证据，不允许自动解除 HOLD。
        if (state == RobotActionExecutionState.UNKNOWN_HOLD || state.terminal()) {
            return;
        }
        switch (event.state()) {
            case ACCEPTED:
                if (state == RobotActionExecutionState.DISPATCH_PENDING
                        || state == RobotActionExecutionState.DISPATCHED) {
                    state = RobotActionExecutionState.ACCEPTED;
                    physicalResultKnown = false;
                }
                break;
            case RUNNING:
                state = RobotActionExecutionState.RUNNING;
                physicalResultKnown = false;
                break;
            case PHYSICAL_DONE:
                finish(RobotActionExecutionState.PHYSICAL_DONE, true, now);
                break;
            case FAILED:
                boolean known = event.error() != null
                        && event.error().path("physicalResultKnown").asBoolean(false);
                if (known) {
                    finish(RobotActionExecutionState.FAILED, true, now);
                } else {
                    state = RobotActionExecutionState.UNKNOWN_HOLD;
                    physicalResultKnown = false;
                    completedAt = now;
                }
                break;
            case UNKNOWN:
                state = RobotActionExecutionState.UNKNOWN_HOLD;
                physicalResultKnown = false;
                completedAt = now;
                break;
            case CANCELLED:
                finish(RobotActionExecutionState.CANCELLED, true, now);
                break;
            default:
                throw new IllegalArgumentException("不支持的机器人动作事件状态：" + event.state());
        }
    }

    private void finish(RobotActionExecutionState terminalState, boolean resultKnown, Instant now) {
        state = terminalState;
        physicalResultKnown = resultKnown;
        completedAt = now;
    }

    private void validateIdentity(RobotActionEvent event) {
        if (!actionInstanceId.equals(event.actionInstanceId())
                || !deviceCommandId.equals(event.deviceCommandId())
                || !robotId.equals(event.robotId())) {
            throw new IllegalArgumentException("动作事件身份与执行记录不匹配");
        }
    }

    public RobotActionExecutionView toView(JsonCodec jsonCodec) {
        return new RobotActionExecutionView(actionInstanceId, robotId, deviceCommandId, actionType,
                actionVersion, templateVersion, requestHash, packageHash, state, physicalResultKnown,
                workflowInstanceId, workflowNodeInstanceId, jsonCodec.readTree(commandInputJson),
                readNullable(jsonCodec, resolvedStepsJson), readNullable(jsonCodec, physicalResultJson),
                readNullable(jsonCodec, errorJson), createdAt, updatedAt, completedAt);
    }

    private JsonNode readNullable(JsonCodec jsonCodec, String json) {
        return json == null ? null : jsonCodec.readTree(json);
    }

    public String getActionInstanceId() { return actionInstanceId; }
    public String getRobotId() { return robotId; }
    public String getRequestHash() { return requestHash; }
    public RobotActionExecutionState getState() { return state; }
    public boolean isPhysicalResultKnown() { return physicalResultKnown; }
    public Instant getCompletedAt() { return completedAt; }

    public boolean isTimedOutAt(Instant now) {
        return !state.terminal() && !createdAt.plusMillis(timeoutMs).isAfter(now);
    }
}
