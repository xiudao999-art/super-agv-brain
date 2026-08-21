package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** 把设备协议事实收敛为执行引擎稳定契约的唯一转换点。 */
@Component
public class ActionExecutionReportMapper {

    private static final String SCHEMA_VERSION = "1.0";

    public ActionExecutionReport fromRobotEvent(ActionExecutionView execution, RobotActionEvent event) {
        ActionExecutionReport.Execution normalized = execution(execution);
        ActionExecutionReport.EventType eventType = eventType(execution.state(), event.phaseEvent());
        return new ActionExecutionReport(
                SCHEMA_VERSION,
                "action-report-" + event.messageId(),
                event.sequence(),
                eventType,
                event.timestamp(),
                correlation(execution),
                normalized,
                step(event),
                execution.state() == ActionExecutionState.PHYSICAL_DONE ? successfulResult(execution) : null,
                failure(execution),
                new ActionExecutionReport.Trace(execution.deviceCommandId(), event.messageId(), event.sequence()));
    }

    /** 为断线、超时和服务重启等 Action 本地状态变化生成同一份稳定契约。 */
    public ActionExecutionReport fromLocalState(ActionExecutionView execution, String reasonCode,
                                                Instant occurredAt) {
        long sequence = occurredAt.toEpochMilli();
        return new ActionExecutionReport(
                SCHEMA_VERSION,
                "action-report-local-" + execution.actionInstanceId() + "-" + reasonCode + "-" + sequence,
                sequence,
                eventType(execution.state(), null),
                occurredAt,
                correlation(execution),
                execution(execution),
                null,
                execution.state() == ActionExecutionState.PHYSICAL_DONE ? successfulResult(execution) : null,
                failure(execution),
                new ActionExecutionReport.Trace(execution.deviceCommandId(), null, null));
    }

    private ActionExecutionReport.Correlation correlation(ActionExecutionView execution) {
        return new ActionExecutionReport.Correlation(
                execution.workflowInstanceId(), execution.workflowNodeInstanceId(),
                execution.actionInstanceId(), execution.actionKey(), execution.robotId());
    }

    private ActionExecutionReport.Execution execution(ActionExecutionView execution) {
        Long durationMs = execution.completedAt() == null ? null
                : Duration.between(execution.createdAt(), execution.completedAt()).toMillis();
        ActionExecutionReport.ExecutionStatus status;
        ActionExecutionReport.PhysicalOutcome physicalOutcome;
        switch (execution.state()) {
            case ACCEPTED:
                status = ActionExecutionReport.ExecutionStatus.ACCEPTED;
                physicalOutcome = ActionExecutionReport.PhysicalOutcome.NOT_STARTED;
                break;
            case PHYSICAL_DONE:
                status = ActionExecutionReport.ExecutionStatus.SUCCEEDED;
                physicalOutcome = ActionExecutionReport.PhysicalOutcome.CONFIRMED_SUCCEEDED;
                break;
            case REJECTED:
                status = ActionExecutionReport.ExecutionStatus.REJECTED;
                physicalOutcome = ActionExecutionReport.PhysicalOutcome.NOT_STARTED;
                break;
            case FAILED:
                status = ActionExecutionReport.ExecutionStatus.FAILED;
                physicalOutcome = ActionExecutionReport.PhysicalOutcome.CONFIRMED_FAILED;
                break;
            case UNKNOWN_HOLD:
                status = ActionExecutionReport.ExecutionStatus.HELD;
                physicalOutcome = ActionExecutionReport.PhysicalOutcome.UNKNOWN;
                break;
            case CANCELLED:
                status = ActionExecutionReport.ExecutionStatus.CANCELLED;
                physicalOutcome = execution.physicalResultKnown()
                        ? ActionExecutionReport.PhysicalOutcome.CONFIRMED_FAILED
                        : ActionExecutionReport.PhysicalOutcome.UNKNOWN;
                break;
            case DISPATCH_PENDING:
            case DISPATCHED:
            case RUNNING:
            default:
                status = ActionExecutionReport.ExecutionStatus.RUNNING;
                physicalOutcome = ActionExecutionReport.PhysicalOutcome.IN_PROGRESS;
        }
        return new ActionExecutionReport.Execution(
                status,
                execution.state().terminal(),
                physicalOutcome,
                execution.createdAt(), execution.completedAt(), durationMs);
    }

    private ActionExecutionReport.EventType eventType(ActionExecutionState state, JsonNode phaseEvent) {
        if (phaseEvent != null && phaseEvent.isObject()) {
            String type = text(phaseEvent, "eventType");
            if ("PHASE_STARTED".equals(type)) return ActionExecutionReport.EventType.STEP_STARTED;
            if ("PHASE_SUCCEEDED".equals(type)) return ActionExecutionReport.EventType.STEP_SUCCEEDED;
            if ("PHASE_RETRY_PENDING".equals(type)) return ActionExecutionReport.EventType.STEP_RETRYING;
            if ("PHASE_SKIPPED".equals(type)) return ActionExecutionReport.EventType.STEP_SKIPPED;
            if ("PHASE_FAILED".equals(type)) return ActionExecutionReport.EventType.STEP_FAILED;
            return ActionExecutionReport.EventType.STEP_PROGRESS;
        }
        switch (state) {
            case ACCEPTED: return ActionExecutionReport.EventType.ACTION_ACCEPTED;
            case PHYSICAL_DONE: return ActionExecutionReport.EventType.ACTION_SUCCEEDED;
            case REJECTED: return ActionExecutionReport.EventType.ACTION_REJECTED;
            case FAILED: return ActionExecutionReport.EventType.ACTION_FAILED;
            case UNKNOWN_HOLD: return ActionExecutionReport.EventType.ACTION_HELD;
            case CANCELLED: return ActionExecutionReport.EventType.ACTION_CANCELLED;
            case DISPATCH_PENDING:
            case DISPATCHED:
            case RUNNING:
            default: return ActionExecutionReport.EventType.ACTION_STARTED;
        }
    }

    private ActionExecutionReport.Step step(RobotActionEvent event) {
        JsonNode phase = event.phaseEvent();
        if (phase != null && phase.isObject()) {
            String configured = configuredPolicy(event);
            String applied = appliedPolicy(text(phase, "eventType"), text(phase, "stepState"));
            ActionExecutionReport.StepPolicy policy = configured == null && applied == null ? null
                    : new ActionExecutionReport.StepPolicy(configured, applied);
            return new ActionExecutionReport.Step(
                    integer(phase, "stepSequence"),
                    text(phase, "phaseId"),
                    text(phase, "subAction"),
                    text(phase, "stepState"),
                    integer(phase, "attempt"),
                    instant(phase.get("startedAt")),
                    instant(phase.get("completedAt")),
                    longValue(phase, "durationMs"),
                    policy,
                    nullableCopy(phase.get("evidence")));
        }
        return terminalStep(event);
    }

    private ActionExecutionReport.Step terminalStep(RobotActionEvent event) {
        JsonNode context = event.error() == null ? null : event.error().path("context");
        JsonNode reported = event.reportState() == null ? null : event.reportState().path("subAction");
        String phaseId = firstText(context, "phaseId", reported, "phaseId");
        String subAction = firstText(context, "subAction", reported, "name");
        if (phaseId == null && subAction == null) return null;
        String status = firstText(context, "subActionState", reported, "state");
        String configured = firstText(context, "onFail", reported, "onFail");
        JsonNode lastStep = lastResolvedStep(event.resolvedSteps(), phaseId);
        Integer sequence = integer(lastStep, "sequence");
        JsonNode evidence = nullableCopy(lastStep == null ? null : lastStep.get("evidence"));
        ActionExecutionReport.StepPolicy policy = configured == null ? null
                : new ActionExecutionReport.StepPolicy(configured, terminalPolicy(configured));
        return new ActionExecutionReport.Step(sequence, phaseId, subAction, status, null,
                null, event.timestamp(), null, policy, evidence);
    }

    private String configuredPolicy(RobotActionEvent event) {
        JsonNode reported = event.reportState() == null ? null : event.reportState().path("subAction");
        return text(reported, "onFail");
    }

    private String firstText(JsonNode first, String firstField, JsonNode second, String secondField) {
        String value = text(first, firstField);
        return value == null || value.trim().isEmpty() ? text(second, secondField) : value;
    }

    private String terminalPolicy(String configured) {
        if ("ABORT".equalsIgnoreCase(configured)) return "ABORTED";
        if (configured != null && configured.toUpperCase(Locale.ROOT).contains("RETRY")) return "EXHAUSTED";
        return configured;
    }

    private JsonNode lastResolvedStep(JsonNode steps, String phaseId) {
        if (steps == null || !steps.isArray()) return null;
        JsonNode found = null;
        for (JsonNode step : steps) {
            if (phaseId == null || phaseId.equals(text(step, "phaseId"))) found = step;
        }
        return found;
    }

    private String appliedPolicy(String phaseEventType, String stepState) {
        if ("PHASE_RETRY_PENDING".equals(phaseEventType)) return "RETRY_PENDING";
        if ("PHASE_SKIPPED".equals(phaseEventType)) return "SKIPPED";
        if ("PHASE_POLICY_APPLIED".equals(phaseEventType)) return stepState;
        return null;
    }

    private ActionExecutionReport.Result successfulResult(ActionExecutionView execution) {
        JsonNode physicalResult = execution.physicalResult();
        JsonNode outputs = physicalResult != null && physicalResult.has("lastOutput")
                ? physicalResult.get("lastOutput").deepCopy()
                : physicalResult == null ? JsonNodeFactory.instance.objectNode() : physicalResult.deepCopy();
        return new ActionExecutionReport.Result(outputs,
                integer(physicalResult, "completedPhaseCount"),
                integer(physicalResult, "totalPhaseCount"),
                countSkipped(execution.resolvedSteps()));
    }

    private Integer integer(JsonNode node, String field) {
        return node != null && node.path(field).canConvertToInt() ? node.path(field).intValue() : null;
    }

    private Long longValue(JsonNode node, String field) {
        return node != null && node.path(field).isNumber() ? node.path(field).longValue() : null;
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return value != null && value.isValueNode() && !value.isNull() ? value.asText() : null;
    }

    private Instant instant(JsonNode value) {
        if (value == null || !value.isTextual()) return null;
        try {
            return Instant.parse(value.textValue());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private JsonNode nullableCopy(JsonNode value) {
        return value == null || value.isNull() ? null : value.deepCopy();
    }

    private ActionExecutionReport.Failure failure(ActionExecutionView execution) {
        if (execution.state() != ActionExecutionState.FAILED
                && execution.state() != ActionExecutionState.REJECTED
                && execution.state() != ActionExecutionState.UNKNOWN_HOLD
                && execution.state() != ActionExecutionState.CANCELLED) {
            return null;
        }
        JsonNode error = execution.error();
        if (execution.state() == ActionExecutionState.REJECTED) {
            return new ActionExecutionReport.Failure(
                    "ACTION.ROBOT_BUSY",
                    ActionExecutionReport.FailureHandling.RETRYABLE,
                    message(error, "机器人当前忙，本次动作未开始执行"),
                    true,
                    deviceError(error),
                    nullableCopy(error));
        }
        if (execution.state() == ActionExecutionState.UNKNOWN_HOLD) {
            return new ActionExecutionReport.Failure(
                    "ACTION.PHYSICAL_RESULT_UNKNOWN",
                    critical(error) ? ActionExecutionReport.FailureHandling.CRITICAL
                            : ActionExecutionReport.FailureHandling.MANUAL_INTERVENTION,
                    message(error, "动作物理执行结果无法确认"),
                    true,
                    deviceError(error),
                    nullableCopy(error));
        }
        if (execution.state() == ActionExecutionState.CANCELLED) {
            return new ActionExecutionReport.Failure(
                    "ACTION.CANCELLED",
                    ActionExecutionReport.FailureHandling.NON_RETRYABLE,
                    message(error, "动作已取消"),
                    true,
                    deviceError(error),
                    nullableCopy(error));
        }
        return new ActionExecutionReport.Failure(
                "ACTION.UNMAPPED_DEVICE_FAILURE",
                critical(error) ? ActionExecutionReport.FailureHandling.CRITICAL
                        : ActionExecutionReport.FailureHandling.MANUAL_INTERVENTION,
                message(error, "动作执行失败"),
                false,
                deviceError(error),
                nullableCopy(error));
    }

    private boolean critical(JsonNode error) {
        return "CRITICAL".equalsIgnoreCase(text(error == null ? null : error.path("detail"), "severity"));
    }

    private String message(JsonNode error, String fallback) {
        String message = text(error, "message");
        return message == null || message.trim().isEmpty() ? fallback : message;
    }

    private ActionExecutionReport.DeviceError deviceError(JsonNode error) {
        if (error == null || !error.isObject()) return null;
        JsonNode physical = error.path("detail").path("physicalDevice");
        if (!physical.isObject()) physical = error.path("physicalDevice");
        String fallbackCode = text(error, "deviceCode");
        if (!physical.isObject() && fallbackCode == null) return null;
        return new ActionExecutionReport.DeviceError(
                text(physical, "deviceType"), text(physical, "vendor"), text(physical, "model"),
                text(physical, "deviceId"),
                text(physical, "code") == null ? fallbackCode : text(physical, "code"),
                text(physical, "message"));
    }

    private Integer countSkipped(JsonNode steps) {
        if (steps == null || !steps.isArray()) return null;
        int count = 0;
        for (JsonNode step : steps) {
            String state = step.path("state").asText("").toUpperCase(Locale.ROOT);
            if (state.contains("SKIP") || state.equals("DISABLED")) count++;
        }
        return count;
    }
}
