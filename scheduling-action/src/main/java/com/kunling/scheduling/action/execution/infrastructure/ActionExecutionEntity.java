package com.kunling.scheduling.action.execution.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.execution.domain.NewActionExecution;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.config.ImmutableCollections;
import com.kunling.scheduling.action.config.JsonCodec;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "action_execution")
public class ActionExecutionEntity {

    @Id
    @Column(name = "action_instance_id", length = 128, nullable = false)
    private String actionInstanceId;

    @Column(name = "robot_id", length = 128, nullable = false)
    private String robotId;

    @Column(name = "device_command_id", length = 128, nullable = false, unique = true)
    private String deviceCommandId;

    @Column(name = "action_key", length = 128, nullable = false)
    private String actionKey;

    @Column(name = "action_revision", nullable = false)
    private long actionRevision;

    @Column(name = "downstream_action_type", length = 64, nullable = false)
    private String downstreamActionType;

    @Column(name = "parameter_set_id", length = 36)
    private String parameterSetId;

    @Column(name = "parameter_set_revision")
    private Long parameterSetRevision;

    @Column(name = "protocol_action_version", length = 16, nullable = false)
    private String protocolActionVersion;

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
    private ActionExecutionState state;

    @Column(name = "physical_result_known", nullable = false)
    private boolean physicalResultKnown;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Lob
    @Column(name = "definition_snapshot_json", nullable = false, columnDefinition = "longtext")
    private String definitionSnapshotJson;

    @Lob
    @Column(name = "parameter_snapshot_json", nullable = false, columnDefinition = "longtext")
    private String parameterSnapshotJson;

    @Lob
    @Column(name = "input_snapshot_json", nullable = false, columnDefinition = "longtext")
    private String inputSnapshotJson;

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

    @Column(name = "last_event_session_id", length = 64)
    private String lastEventSessionId;

    @Column(name = "last_event_sequence")
    private Long lastEventSequence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected ActionExecutionEntity() {
    }

    public ActionExecutionEntity(NewActionExecution execution, JsonCodec jsonCodec) {
        this.actionInstanceId = execution.actionInstanceId();
        this.robotId = execution.robotId();
        this.deviceCommandId = execution.deviceCommandId();
        this.actionKey = execution.actionKey();
        this.actionRevision = execution.actionRevision();
        this.downstreamActionType = execution.downstreamActionType();
        this.parameterSetId = execution.parameterSetId();
        this.parameterSetRevision = execution.parameterSetRevision();
        this.protocolActionVersion = execution.protocolActionVersion();
        this.requestHash = execution.requestHash();
        this.packageHash = execution.packageHash();
        this.workflowInstanceId = execution.workflowInstanceId();
        this.workflowNodeInstanceId = execution.workflowNodeInstanceId();
        this.state = ActionExecutionState.DISPATCH_PENDING;
        this.physicalResultKnown = true;
        this.timeoutMs = execution.timeoutMs();
        this.definitionSnapshotJson = jsonCodec.write(execution.definitionSnapshot());
        this.parameterSnapshotJson = jsonCodec.write(execution.parameterSnapshot());
        this.inputSnapshotJson = jsonCodec.write(execution.inputSnapshot());
        this.commandInputJson = jsonCodec.write(execution.commandInput());
        this.createdAt = execution.createdAt();
        this.updatedAt = execution.createdAt();
    }

    public void markDispatched(String sessionId, String messageId, Instant sentAt) {
        this.dispatchSessionId = sessionId;
        this.dispatchMessageId = messageId;
        if (state == ActionExecutionState.DISPATCH_PENDING) {
            state = ActionExecutionState.DISPATCHED;
            physicalResultKnown = false;
        }
        updatedAt = sentAt;
    }

    public void hold(String code, String message, JsonCodec jsonCodec, Instant now) {
        if (state.terminal() && state != ActionExecutionState.UNKNOWN_HOLD) {
            return;
        }
        state = ActionExecutionState.UNKNOWN_HOLD;
        physicalResultKnown = false;
        errorJson = jsonCodec.write(ImmutableCollections.mapOf("code", code, "message", message));
        updatedAt = now;
        completedAt = now;
    }

    /** 返回该事件是否可以继续转换为执行引擎报告。 */
    public boolean applyEvent(RobotActionEvent event, JsonCodec jsonCodec, Instant now) {
        validateIdentity(event);
        if (event.sessionId().equals(lastEventSessionId)
                && lastEventSequence != null && event.sequence() <= lastEventSequence) {
            return false;
        }
        lastEventSequence = event.sequence();
        lastEventSessionId = event.sessionId();
        lastEventMessageId = event.messageId();
        if (event.resolvedSteps() != null) resolvedStepsJson = jsonCodec.write(event.resolvedSteps());
        if (event.physicalResult() != null) physicalResultJson = jsonCodec.write(event.physicalResult());
        if (event.error() != null) errorJson = jsonCodec.write(event.error());
        updatedAt = now;

        // HOLD 是人工处置边界；迟到消息只补证据，不自动解除。
        if (state == ActionExecutionState.UNKNOWN_HOLD || state.terminal()) {
            return false;
        }
        switch (event.state()) {
            case ACCEPTED:
                if (state == ActionExecutionState.DISPATCH_PENDING || state == ActionExecutionState.DISPATCHED) {
                    state = ActionExecutionState.ACCEPTED;
                    physicalResultKnown = false;
                    return true;
                }
                return false;
            case RUNNING:
                state = ActionExecutionState.RUNNING;
                physicalResultKnown = false;
                return true;
            case PHYSICAL_DONE:
                finish(ActionExecutionState.PHYSICAL_DONE, true, now);
                return true;
            case REJECTED:
                // BUSY 表示设备没有接收本次动作，物理执行明确未开始，不能混同执行失败。
                finish(ActionExecutionState.REJECTED, true, now);
                return true;
            case FAILED:
                boolean known = event.error() != null
                        && event.error().path("physicalResultKnown").asBoolean(false);
                if (known) finish(ActionExecutionState.FAILED, true, now);
                else enterUnknownHold(now);
                return true;
            case UNKNOWN:
                if (errorJson == null) {
                    errorJson = jsonCodec.write(ImmutableCollections.mapOf(
                            "code", "PHYSICAL_RESULT_UNKNOWN", "message", "下游报告物理结果未知"));
                }
                enterUnknownHold(now);
                return true;
            case CANCELLED:
                finish(ActionExecutionState.CANCELLED, true, now);
                return true;
            default:
                throw new IllegalArgumentException("不支持的机器人动作事件状态：" + event.state());
        }
    }

    public ActionExecutionView toView(JsonCodec jsonCodec) {
        return new ActionExecutionView(actionInstanceId, robotId, deviceCommandId, actionKey,
                actionRevision, downstreamActionType, parameterSetId, parameterSetRevision,
                protocolActionVersion, requestHash, packageHash, state, physicalResultKnown,
                workflowInstanceId, workflowNodeInstanceId, jsonCodec.readTree(definitionSnapshotJson),
                jsonCodec.readTree(parameterSnapshotJson), jsonCodec.readTree(inputSnapshotJson),
                jsonCodec.readTree(commandInputJson), readNullable(jsonCodec, resolvedStepsJson),
                readNullable(jsonCodec, physicalResultJson), readNullable(jsonCodec, errorJson),
                createdAt, updatedAt, completedAt);
    }

    public boolean isTimedOutAt(Instant now) {
        return !state.terminal() && !createdAt.plusMillis(timeoutMs).isAfter(now);
    }

    private void finish(ActionExecutionState terminalState, boolean resultKnown, Instant now) {
        state = terminalState;
        physicalResultKnown = resultKnown;
        updatedAt = now;
        completedAt = now;
    }

    /** 保留下游原始 error JSON，只收紧执行态。 */
    private void enterUnknownHold(Instant now) {
        state = ActionExecutionState.UNKNOWN_HOLD;
        physicalResultKnown = false;
        updatedAt = now;
        completedAt = now;
    }

    private void validateIdentity(RobotActionEvent event) {
        if (!actionInstanceId.equals(event.actionInstanceId())
                || !deviceCommandId.equals(event.deviceCommandId())
                || !robotId.equals(event.robotId())) {
            throw new IllegalArgumentException("动作事件身份与执行快照不匹配。");
        }
    }

    private JsonNode readNullable(JsonCodec jsonCodec, String json) {
        return json == null ? null : jsonCodec.readTree(json);
    }

    public String getActionInstanceId() { return actionInstanceId; }
    public String getRobotId() { return robotId; }
    public String getActionKey() { return actionKey; }
    public String getParameterSetId() { return parameterSetId; }
    public String getRequestHash() { return requestHash; }
    public ActionExecutionState getState() { return state; }
}
