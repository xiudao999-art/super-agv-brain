package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.exceptionmapping.application.BusinessErrorDecision;
import com.kunling.scheduling.action.exceptionmapping.application.BusinessErrorMappingEngine;
import com.kunling.scheduling.action.exceptionmapping.application.ErrorMappingContext;
import com.kunling.scheduling.action.exceptionmapping.domain.BusinessDisposition;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** 把 Action 最终状态和冻结的异常映射快照收敛为一次统一业务结果。 */
@Component
public class ActionExecutionReportMapper {
    private final BusinessErrorMappingEngine mappingEngine;

    @Autowired
    public ActionExecutionReportMapper(BusinessErrorMappingEngine mappingEngine) {
        this.mappingEngine = mappingEngine;
    }

    /** 仅供不加载 Spring 的模块测试使用。 */
    public ActionExecutionReportMapper() {
        this(new BusinessErrorMappingEngine());
    }

    /** 非终态事件只用于步骤日志，不生成执行引擎报告。 */
    public Optional<ActionExecutionReport> fromTerminalRobotEvent(ActionExecutionView execution,
                                                                   RobotActionEvent event) {
        if (execution == null || !execution.state().terminal()) return Optional.empty();
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
        ErrorMappingContext failureContext = success ? null
                : mappingContext(execution.error(), execution.physicalResultKnown());
        BusinessErrorDecision mappingDecision = execution.state() == ActionExecutionState.FAILED
                ? mappingDecision(execution, failureContext) : null;
        PhysicalOutcome physicalOutcome = success
                ? PhysicalOutcome.CONFIRMED_SUCCEEDED
                : resolvePhysicalOutcome(execution, mappingDecision);
        return new ActionExecutionReport(
                execution.workflowInstanceId(), execution.workflowNodeInstanceId(),
                execution.actionInstanceId(), execution.actionKey(), execution.robotId(),
                success, success || execution.physicalResultKnown(), physicalOutcome, completedAt,
                success ? successfulOutput(execution.physicalResult()) : null,
                success ? null : failure(execution, failureContext, mappingDecision));
    }

    private JsonNode successfulOutput(JsonNode physicalResult) {
        if (physicalResult == null || physicalResult.isNull()) return JsonNodeFactory.instance.objectNode();
        JsonNode lastOutput = physicalResult.get("lastOutput");
        return lastOutput == null || lastOutput.isNull()
                ? physicalResult.deepCopy() : lastOutput.deepCopy();
    }

    private ActionExecutionReport.Failure failure(ActionExecutionView execution,
                                                    ErrorMappingContext context,
                                                    BusinessErrorDecision decision) {
        JsonNode error = execution.error();
        switch (execution.state()) {
            case REJECTED:
                return fixedFailure("ACTION.ROBOT_BUSY", "ROBOT.BUSY",
                        BusinessDisposition.RETRYABLE, "机器人当前忙，本次动作未开始执行",
                        null, error, context);
            case UNKNOWN_HOLD:
                return fixedFailure("ACTION.PHYSICAL_RESULT_UNKNOWN", "ACTION.PHYSICAL_RESULT_UNKNOWN",
                        critical(error) ? BusinessDisposition.CRITICAL
                                : BusinessDisposition.MANUAL_INTERVENTION,
                        "动作物理执行结果无法确认", "必须先查询或人工核验现场状态",
                        error, context);
            case CANCELLED:
                return fixedFailure("ACTION.CANCELLED", "ACTION.CANCELLED",
                        BusinessDisposition.NON_RETRYABLE, "动作已取消", null,
                        error, context);
            case FAILED:
            default:
                BusinessDisposition disposition = critical(error)
                        ? BusinessDisposition.CRITICAL : decision.businessDisposition();
                return new ActionExecutionReport.Failure(
                        context.phaseId(), context.subAction(), decision.businessCode(),
                        decision.reasonCode(), disposition,
                        decision.matchedRuleId(), decision.mappingProfileId(),
                        defaultString(decision.businessMessage(), "动作执行失败"),
                        decision.handlingAdvice(),
                        robotClientFault(error), deviceError(error));
        }
    }

    private ActionExecutionReport.Failure fixedFailure(String businessCode,
                                                       String reasonCode,
                                                       BusinessDisposition disposition,
                                                       String fallbackMessage,
                                                       String handlingAdvice,
                                                       JsonNode error,
                                                       ErrorMappingContext context) {
        return new ActionExecutionReport.Failure(context.phaseId(), context.subAction(),
                businessCode, reasonCode, disposition,
                null, null, message(error, fallbackMessage), handlingAdvice,
                robotClientFault(error), deviceError(error));
    }

    private BusinessErrorDecision mappingDecision(ActionExecutionView execution,
                                                   ErrorMappingContext context) {
        JsonNode snapshot = execution.commandInput() == null ? null
                : execution.commandInput().path("errorPolicySnapshot");
        return mappingEngine.resolve(snapshot, context);
    }

    private PhysicalOutcome resolvePhysicalOutcome(ActionExecutionView execution,
                                                    BusinessErrorDecision mappingDecision) {
        if (!execution.physicalResultKnown()) return PhysicalOutcome.UNKNOWN;
        String reported = firstNonBlank(
                text(execution.error() == null ? null : execution.error().path("detail"), "physicalOutcome"),
                text(execution.error(), "physicalOutcome"));
        if (reported != null) {
            try {
                return PhysicalOutcome.valueOf(reported.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // 下游旧版本未使用统一枚举时，按已确认失败保守收敛。
            }
        }
        if (mappingDecision != null
                && mappingDecision.physicalOutcome() != PhysicalOutcome.UNKNOWN) {
            return mappingDecision.physicalOutcome();
        }
        return PhysicalOutcome.CONFIRMED_FAILED;
    }

    private ErrorMappingContext mappingContext(JsonNode error, boolean physicalResultKnown) {
        JsonNode detail = error == null ? null : error.path("detail");
        JsonNode context = error == null ? null : error.path("context");
        JsonNode physical = detail == null ? null : detail.path("physicalDevice");
        if (physical == null || !physical.isObject()) physical = error == null ? null : error.path("physicalDevice");
        return new ErrorMappingContext(
                firstNonBlank(text(context, "phaseId"), text(detail, "phaseId")),
                firstNonBlank(text(context, "subAction"), text(detail, "subAction")),
                text(physical, "vendor"), text(physical, "deviceType"),
                firstNonBlank(text(physical, "code"), text(error, "deviceCode")),
                firstNonBlank(text(physical, "message"), text(error, "deviceMessage")),
                physicalResultKnown);
    }

    private boolean critical(JsonNode error) {
        return "CRITICAL".equalsIgnoreCase(firstNonBlank(
                text(error == null ? null : error.path("detail"), "severity"),
                text(error, "severity")));
    }

    private String message(JsonNode error, String fallback) {
        String message = text(error, "message");
        return message == null || message.trim().isEmpty() ? fallback : message;
    }

    private ActionExecutionReport.RobotClientFault robotClientFault(JsonNode error) {
        if (error == null || !error.isObject()) return null;
        JsonNode detail = error.path("detail");
        return new ActionExecutionReport.RobotClientFault(
                text(error, "code"), text(error, "message"),
                firstNonBlank(text(detail, "category"), text(error, "category")),
                firstNonBlank(text(detail, "severity"), text(error, "severity")),
                firstNonBlank(text(detail, "recoveryStrategy"), text(error, "recoveryStrategy")),
                error.path("retryable").asBoolean(false), error.deepCopy());
    }

    private ActionExecutionReport.DeviceError deviceError(JsonNode error) {
        if (error == null || !error.isObject()) return null;
        JsonNode physical = error.path("detail").path("physicalDevice");
        if (!physical.isObject()) physical = error.path("physicalDevice");

        String deviceType = text(physical, "deviceType");
        String vendor = text(physical, "vendor");
        String model = text(physical, "model");
        String deviceId = text(physical, "deviceId");
        String adapterKey = text(physical, "adapterKey");
        String adapterVersion = text(physical, "adapterVersion");
        String code = firstNonBlank(text(physical, "code"), text(error, "deviceCode"));
        String message = firstNonBlank(text(physical, "message"), text(error, "deviceMessage"),
                code == null ? null : text(error, "message"));
        if (deviceType == null && vendor == null && model == null && deviceId == null
                && adapterKey == null && code == null && message == null) return null;
        return new ActionExecutionReport.DeviceError(deviceType, vendor, model, deviceId,
                adapterKey, adapterVersion, code, message,
                physical != null && physical.isObject() ? physical.deepCopy() : null);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return null;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) return null;
        JsonNode value = node.get(field);
        return value != null && value.isValueNode() && !value.isNull() ? value.asText() : null;
    }
}
