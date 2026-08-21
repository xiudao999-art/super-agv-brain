package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** 把 Action 最终状态收敛为执行引擎只需处理一次的成功/失败结果。 */
@Component
public class ActionExecutionReportMapper {

    /** 非终态事件只用于步骤日志，不生成执行引擎报告。 */
    public Optional<ActionExecutionReport> fromTerminalRobotEvent(ActionExecutionView execution,
                                                                   RobotActionEvent event) {
        if (execution == null || !execution.state().terminal()) {
            return Optional.empty();
        }
        Instant completedAt = execution.completedAt() == null ? event.timestamp() : execution.completedAt();
        return Optional.of(toReport(execution, completedAt));
    }

    /** 断线、超时和服务重启会直接形成一次最终失败报告。 */
    public ActionExecutionReport fromLocalState(ActionExecutionView execution, Instant occurredAt) {
        if (execution == null || !execution.state().terminal()) {
            throw new IllegalArgumentException("只有终态执行才能生成最终报告。");
        }
        Instant completedAt = execution.completedAt() == null ? occurredAt : execution.completedAt();
        return toReport(execution, completedAt);
    }

    private ActionExecutionReport toReport(ActionExecutionView execution, Instant completedAt) {
        boolean success = execution.state() == ActionExecutionState.PHYSICAL_DONE;
        return new ActionExecutionReport(
                execution.workflowInstanceId(),
                execution.workflowNodeInstanceId(),
                execution.actionInstanceId(),
                execution.actionKey(),
                execution.robotId(),
                success,
                success || execution.physicalResultKnown(),
                completedAt,
                success ? successfulOutput(execution.physicalResult()) : null,
                success ? null : failure(execution));
    }

    private JsonNode successfulOutput(JsonNode physicalResult) {
        if (physicalResult == null || physicalResult.isNull()) {
            return JsonNodeFactory.instance.objectNode();
        }
        JsonNode lastOutput = physicalResult.get("lastOutput");
        return lastOutput == null || lastOutput.isNull()
                ? physicalResult.deepCopy() : lastOutput.deepCopy();
    }

    private ActionExecutionReport.Failure failure(ActionExecutionView execution) {
        JsonNode error = execution.error();
        switch (execution.state()) {
            case REJECTED:
                return new ActionExecutionReport.Failure(
                        "ACTION.ROBOT_BUSY",
                        ActionExecutionReport.FailureHandling.RETRYABLE,
                        message(error, "机器人当前忙，本次动作未开始执行"),
                        deviceError(error));
            case UNKNOWN_HOLD:
                return new ActionExecutionReport.Failure(
                        "ACTION.PHYSICAL_RESULT_UNKNOWN",
                        critical(error) ? ActionExecutionReport.FailureHandling.CRITICAL
                                : ActionExecutionReport.FailureHandling.MANUAL_INTERVENTION,
                        message(error, "动作物理执行结果无法确认"),
                        deviceError(error));
            case CANCELLED:
                return new ActionExecutionReport.Failure(
                        "ACTION.CANCELLED",
                        ActionExecutionReport.FailureHandling.NON_RETRYABLE,
                        message(error, "动作已取消"),
                        deviceError(error));
            case FAILED:
            default:
                return new ActionExecutionReport.Failure(
                        "ACTION.UNMAPPED_DEVICE_FAILURE",
                        critical(error) ? ActionExecutionReport.FailureHandling.CRITICAL
                                : ActionExecutionReport.FailureHandling.MANUAL_INTERVENTION,
                        message(error, "动作执行失败"),
                        deviceError(error));
        }
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

        String deviceType = text(physical, "deviceType");
        String vendor = text(physical, "vendor");
        String deviceId = text(physical, "deviceId");
        String code = firstNonBlank(text(physical, "code"), text(error, "deviceCode"), text(error, "code"));
        String message = firstNonBlank(text(physical, "message"), text(error, "message"));
        if (deviceType == null && vendor == null && deviceId == null && code == null && message == null) {
            return null;
        }
        return new ActionExecutionReport.DeviceError(deviceType, vendor, deviceId, code, message);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) return null;
        JsonNode value = node.get(field);
        return value != null && value.isValueNode() && !value.isNull() ? value.asText() : null;
    }
}
