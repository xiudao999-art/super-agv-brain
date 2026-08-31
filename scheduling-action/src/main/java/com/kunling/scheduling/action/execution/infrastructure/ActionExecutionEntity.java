package com.kunling.scheduling.action.execution.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.execution.domain.ActionEventApplyResult;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.execution.domain.NewActionExecution;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
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
import java.util.Iterator;
import java.util.Set;

@Entity
@Table(name = "action_execution")
public class ActionExecutionEntity {
    private static final Set<String> ERROR_FIELDS = ImmutableCollections.setOf(
            "clientCode", "message", "deviceFault");

    @Id
    @Column(name = "action_instance_id", length = 128, nullable = false)
    private String actionInstanceId;

    @Column(name = "robot_id", length = 128, nullable = false)
    private String robotId;

    @Column(name = "device_command_id", length = 128, nullable = false, unique = true)
    private String deviceCommandId;

    @Column(name = "action_definition_id", length = 36, nullable = false)
    private String actionDefinitionId;

    @Column(name = "protocol_version", length = 16, nullable = false)
    private String protocolVersion;

    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    @Column(name = "package_hash", length = 64, nullable = false)
    private String packageHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 32, nullable = false)
    private ActionExecutionState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "physical_outcome", length = 32, nullable = false)
    private PhysicalOutcome physicalOutcome;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Lob
    @Column(name = "command_input_json", nullable = false, columnDefinition = "longtext")
    private String commandInputJson;

    @Lob
    @Column(name = "resolved_steps_json", columnDefinition = "longtext")
    private String resolvedStepsJson;

    @Lob
    @Column(name = "last_step_event_json", columnDefinition = "longtext")
    private String lastStepEventJson;

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
        this.actionDefinitionId = execution.actionDefinitionId();
        this.robotId = execution.robotId();
        this.deviceCommandId = execution.deviceCommandId();
        this.protocolVersion = execution.protocolVersion();
        this.requestHash = execution.requestHash();
        this.packageHash = execution.packageHash();
        this.state = ActionExecutionState.DISPATCH_PENDING;
        this.physicalOutcome = PhysicalOutcome.NOT_STARTED;
        this.timeoutMs = execution.timeoutMs();
        this.commandInputJson = jsonCodec.write(execution.commandInput());
        this.createdAt = execution.createdAt();
        this.updatedAt = execution.createdAt();
    }

    public void markDispatched(String sessionId, String messageId, Instant sentAt) {
        this.dispatchSessionId = sessionId;
        this.dispatchMessageId = messageId;
        if (state == ActionExecutionState.DISPATCH_PENDING) {
            state = ActionExecutionState.DISPATCHED;
            physicalOutcome = PhysicalOutcome.UNKNOWN;
        }
        // 对端可能在 send 返回后立即回报事件，不能让稍后落库的发送回执把事件时间倒退。
        if (updatedAt == null || updatedAt.isBefore(sentAt)) {
            updatedAt = sentAt;
        }
    }

    public void hold(String code, String message, JsonCodec jsonCodec, Instant now) {
        if (state.terminal() && state != ActionExecutionState.UNKNOWN_HOLD) {
            return;
        }
        state = ActionExecutionState.UNKNOWN_HOLD;
        physicalOutcome = PhysicalOutcome.UNKNOWN;
        errorJson = jsonCodec.write(ImmutableCollections.mapOf("code", code, "message", message));
        updatedAt = now;
        completedAt = now;
    }

    /**
     * 应用一个 ACTION_EVENT。
     *
     * <p>sequence 以 actionInstanceId 为作用域全局递增，不随 TCP session 重置。
     * 因此重连后收到的旧序号也必须丢弃，不能写入执行证据表。</p>
     */
    public ActionEventApplyResult applyEvent(RobotActionEvent event, JsonCodec jsonCodec, Instant now) {
        if (event == null) throw new IllegalArgumentException("动作事件不能为空。");
        validateIdentity(event);
        if (event.sequence() <= 0L) {
            throw new IllegalArgumentException("动作事件 sequence 必须是正整数。");
        }
        if (lastEventSequence != null && event.sequence() <= lastEventSequence) {
            return ActionEventApplyResult.DROPPED;
        }
        validateEventContract(event, jsonCodec);
        lastEventSequence = event.sequence();
        lastEventSessionId = event.sessionId();
        lastEventMessageId = event.messageId();
        if (event.resolvedSteps() != null) resolvedStepsJson = jsonCodec.write(event.resolvedSteps());
        if (event.stepEvent() != null) lastStepEventJson = jsonCodec.write(event.stepEvent());
        if (event.error() != null) errorJson = jsonCodec.write(event.error());
        updatedAt = now;

        // HOLD 是人工处置边界；迟到消息只补证据，不自动解除。
        if (state == ActionExecutionState.UNKNOWN_HOLD || state.terminal()) {
            return ActionEventApplyResult.EVIDENCE_ONLY;
        }
        switch (event.state()) {
            case ACCEPTED:
                if (state == ActionExecutionState.DISPATCH_PENDING || state == ActionExecutionState.DISPATCHED) {
                    state = ActionExecutionState.ACCEPTED;
                    updatePhysicalOutcome(event);
                    return ActionEventApplyResult.APPLIED;
                }
                return ActionEventApplyResult.EVIDENCE_ONLY;
            case RUNNING:
                state = ActionExecutionState.RUNNING;
                updatePhysicalOutcome(event);
                return ActionEventApplyResult.APPLIED;
            case FINISHED:
                requireOutcome(event, PhysicalOutcome.CONFIRMED_SUCCEEDED);
                finish(ActionExecutionState.FINISHED, event.physicalOutcome(), now);
                return ActionEventApplyResult.APPLIED;
            case REJECTED:
                requireOutcome(event, PhysicalOutcome.NOT_STARTED);
                finish(ActionExecutionState.REJECTED, event.physicalOutcome(), now);
                return ActionEventApplyResult.APPLIED;
            case FAILED:
                requireFailedOutcome(event);
                if (isUncertain(event.physicalOutcome())) enterUnknownHold(event.physicalOutcome(), now);
                else finish(ActionExecutionState.FAILED, event.physicalOutcome(), now);
                return ActionEventApplyResult.APPLIED;
            case UNKNOWN:
                requireOutcome(event, PhysicalOutcome.UNKNOWN);
                if (errorJson == null) {
                    errorJson = jsonCodec.write(ImmutableCollections.mapOf(
                            "code", "PHYSICAL_RESULT_UNKNOWN", "message", "下游报告物理结果未知"));
                }
                enterUnknownHold(event.physicalOutcome(), now);
                return ActionEventApplyResult.APPLIED;
            default:
                throw new IllegalArgumentException("不支持的机器人动作事件状态：" + event.state());
        }
    }

    public ActionExecutionView toView(JsonCodec jsonCodec) {
        return new ActionExecutionView(actionInstanceId, actionDefinitionId, robotId, deviceCommandId,
                protocolVersion, requestHash, packageHash, state, physicalOutcome, timeoutMs,
                jsonCodec.readTree(commandInputJson), readNullable(jsonCodec, lastStepEventJson),
                readNullable(jsonCodec, resolvedStepsJson), readNullable(jsonCodec, errorJson),
                dispatchSessionId, dispatchMessageId, lastEventMessageId, lastEventSessionId,
                lastEventSequence, createdAt, updatedAt, completedAt);
    }

    public boolean isTimedOutAt(Instant now) {
        return !state.terminal() && !createdAt.plusMillis(timeoutMs).isAfter(now);
    }

    private void finish(ActionExecutionState terminalState, PhysicalOutcome outcome, Instant now) {
        state = terminalState;
        physicalOutcome = outcome;
        updatedAt = now;
        completedAt = now;
    }

    /** 保留下游原始 error JSON，只收紧执行态。 */
    private void enterUnknownHold(PhysicalOutcome outcome, Instant now) {
        state = ActionExecutionState.UNKNOWN_HOLD;
        physicalOutcome = outcome;
        updatedAt = now;
        completedAt = now;
    }

    private void updatePhysicalOutcome(RobotActionEvent event) {
        if (event.physicalOutcome() != null) physicalOutcome = event.physicalOutcome();
    }

    private void requireOutcome(RobotActionEvent event, PhysicalOutcome expected) {
        if (event.physicalOutcome() != expected) {
            throw new IllegalArgumentException(event.state() + " 状态必须携带 " + expected + "。");
        }
    }

    private void requireFailedOutcome(RobotActionEvent event) {
        if (event.physicalOutcome() != PhysicalOutcome.CONFIRMED_FAILED
                && event.physicalOutcome() != PhysicalOutcome.PARTIALLY_COMPLETED
                && event.physicalOutcome() != PhysicalOutcome.UNKNOWN) {
            throw new IllegalArgumentException(
                    "FAILED 状态只能携带 CONFIRMED_FAILED、PARTIALLY_COMPLETED 或 UNKNOWN。");
        }
    }

    private boolean isUncertain(PhysicalOutcome outcome) {
        return outcome == PhysicalOutcome.UNKNOWN || outcome == PhysicalOutcome.PARTIALLY_COMPLETED;
    }

    private void validateIdentity(RobotActionEvent event) {
        if (!actionInstanceId.equals(event.actionInstanceId())
                || !deviceCommandId.equals(event.deviceCommandId())
                || !robotId.equals(event.robotId())) {
            throw new IllegalArgumentException("动作事件身份与执行记录不匹配。");
        }
        if (dispatchSessionId != null && !dispatchSessionId.equals(event.sessionId())) {
            throw new IllegalArgumentException("动作事件不属于下发 COMMAND 的原会话，禁止跨会话恢复旧动作。");
        }
    }

    /** 先完成无副作用契约校验，非法事件不能推进序号或覆盖执行证据。 */
    private void validateEventContract(RobotActionEvent event, JsonCodec jsonCodec) {
        switch (event.state()) {
            case FINISHED:
                requireOutcome(event, PhysicalOutcome.CONFIRMED_SUCCEEDED);
                break;
            case REJECTED:
                requireOutcome(event, PhysicalOutcome.NOT_STARTED);
                break;
            case FAILED:
                requireFailedOutcome(event);
                break;
            case UNKNOWN:
                requireOutcome(event, PhysicalOutcome.UNKNOWN);
                break;
            default:
                break;
        }
        validateEventEvidence(event, jsonCodec);
    }

    private void validateEventEvidence(RobotActionEvent event, JsonCodec jsonCodec) {
        if (event.stepEvent() != null && !event.stepEvent().isObject()) {
            throw new IllegalArgumentException("stepEvent 必须是 JSON 对象。");
        }
        if (event.resolvedSteps() != null && !event.resolvedSteps().isArray()) {
            throw new IllegalArgumentException("resolvedSteps 必须是 JSON 数组。");
        }
        boolean terminal = event.state() == RobotActionEvent.State.FINISHED
                || event.state() == RobotActionEvent.State.REJECTED
                || event.state() == RobotActionEvent.State.FAILED
                || event.state() == RobotActionEvent.State.UNKNOWN;
        boolean resolvedStepsRequired = terminal && (event.physicalOutcome() == PhysicalOutcome.PARTIALLY_COMPLETED
                || executionStepCount(jsonCodec) > 1);
        if (resolvedStepsRequired && event.resolvedSteps() == null) {
            throw new IllegalArgumentException("多步骤或部分完成的终态必须携带 resolvedSteps。");
        }
        validateError(event, terminal && event.state() != RobotActionEvent.State.FINISHED);
    }

    private int executionStepCount(JsonCodec jsonCodec) {
        JsonNode steps = jsonCodec.readTree(commandInputJson).at("/executionPlan/steps");
        return steps.isArray() ? steps.size() : 0;
    }

    private void validateError(RobotActionEvent event, boolean required) {
        JsonNode error = event.error();
        if (error == null || error.isNull()) {
            if (required) throw new IllegalArgumentException(event.state() + " 终态必须携带 error。");
            return;
        }
        if (!error.isObject()) throw new IllegalArgumentException("error 必须是 JSON 对象。");
        Iterator<String> fields = error.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!ERROR_FIELDS.contains(field)) {
                throw new IllegalArgumentException("error 不允许携带业务字段或未知字段：" + field);
            }
        }
        JsonNode clientCode = error.get("clientCode");
        if (clientCode == null || !clientCode.isIntegralNumber()
                || !clientCode.canConvertToInt() || clientCode.intValue() <= 0) {
            throw new IllegalArgumentException("error.clientCode 必须是正整数技术码。");
        }
        requireErrorText(error, "message");

        JsonNode deviceFault = error.get("deviceFault");
        if (deviceFault == null || deviceFault.isNull()) return;
        if (!deviceFault.isObject()) {
            throw new IllegalArgumentException("error.deviceFault 必须是 JSON 对象或 null。");
        }
        String vendor = requireErrorText(deviceFault, "vendor");
        String deviceType = requireErrorText(deviceFault, "deviceType");
        if (!vendor.matches("[A-Z0-9][A-Z0-9._-]{0,127}")
                || !deviceType.matches("[A-Z0-9][A-Z0-9._-]{0,127}")) {
            throw new IllegalArgumentException("deviceFault.vendor 和 deviceType 必须是稳定的大写标识。");
        }
        requireErrorText(deviceFault, "code");
        requireErrorText(deviceFault, "message");
        validateOptionalErrorText(deviceFault, "model");
        validateOptionalErrorText(deviceFault, "deviceId");
    }

    private String requireErrorText(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual() || value.textValue().trim().isEmpty()) {
            throw new IllegalArgumentException("error." + field + " 必须是非空字符串。");
        }
        return value.textValue();
    }

    private void validateOptionalErrorText(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value != null && !value.isNull() && !value.isTextual()) {
            throw new IllegalArgumentException("error.deviceFault." + field + " 必须是字符串。");
        }
    }

    private JsonNode readNullable(JsonCodec jsonCodec, String json) {
        return json == null ? null : jsonCodec.readTree(json);
    }

    public String getActionInstanceId() { return actionInstanceId; }
    public String getRobotId() { return robotId; }
    public String getActionDefinitionId() { return actionDefinitionId; }
    public String getRequestHash() { return requestHash; }
    public ActionExecutionState getState() { return state; }
}
