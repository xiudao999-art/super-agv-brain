package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.exceptionmapping.application.ActionErrorMappingRuleService;
import com.kunling.scheduling.action.exceptionmapping.application.BusinessErrorDecision;
import com.kunling.scheduling.action.exceptionmapping.application.BusinessErrorMappingEngine;
import com.kunling.scheduling.action.exceptionmapping.application.ClientFaultCatalog;
import com.kunling.scheduling.action.exceptionmapping.application.ErrorMappingContext;
import com.kunling.scheduling.action.exceptionmapping.domain.HandlingConstraint;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;
import com.kunling.scheduling.action.execution.domain.ActionExecutionResult;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** 将 Action 终态映射为执行引擎只需理解的最小最终报告。 */
@Component
public class ActionExecutionReportMapper {
    private final BusinessErrorMappingEngine mappingEngine;
    private final ActionErrorMappingRuleService mappingRuleService;
    private final ClientFaultCatalog clientFaultCatalog;

    public ActionExecutionReportMapper(BusinessErrorMappingEngine mappingEngine,
                                       ActionErrorMappingRuleService mappingRuleService,
                                       ClientFaultCatalog clientFaultCatalog) {
        this.mappingEngine = mappingEngine;
        this.mappingRuleService = mappingRuleService;
        this.clientFaultCatalog = clientFaultCatalog;
    }

    public Optional<ActionExecutionReport> fromTerminalRobotEvent(ActionExecutionView execution,
                                                                   RobotActionEvent event) {
        if (execution == null || !execution.state().terminal()) return Optional.empty();
        return Optional.of(toReport(execution));
    }

    public ActionExecutionReport fromLocalState(ActionExecutionView execution, Instant occurredAt) {
        if (execution == null || !execution.state().terminal()) {
            throw new IllegalArgumentException("只有终态执行才能生成最终报告。");
        }
        return toReport(execution);
    }

    private ActionExecutionReport toReport(ActionExecutionView execution) {
        ActionExecutionResult result = result(execution.state());
        return new ActionExecutionReport(execution.actionInstanceId(), result,
                execution.physicalOutcome(), result == ActionExecutionResult.SUCCEEDED
                ? null : failure(execution));
    }

    private ActionExecutionResult result(ActionExecutionState state) {
        if (state == ActionExecutionState.FINISHED) return ActionExecutionResult.SUCCEEDED;
        if (state == ActionExecutionState.UNKNOWN_HOLD) return ActionExecutionResult.UNKNOWN_HOLD;
        return ActionExecutionResult.FAILED;
    }

    private ActionExecutionReport.Failure failure(ActionExecutionView execution) {
        ErrorMappingContext context = mappingContext(execution);
        ActionExecutionReport.DeviceFault deviceFault = deviceFault(execution.error());
        PhysicalOutcome outcome = execution.physicalOutcome();

        if (outcome == PhysicalOutcome.UNKNOWN || outcome == PhysicalOutcome.PARTIALLY_COMPLETED) {
            return new ActionExecutionReport.Failure(context.stepId(),
                    "ACTION.PHYSICAL_RESULT_UNCERTAIN", HandlingConstraint.MANUAL_INTERVENTION,
                    message(execution.error(), "动作物理执行结果无法确认"), deviceFault);
        }
        if (deviceFault != null && hasText(deviceFault.code())) {
            BusinessErrorDecision decision = mappingEngine.resolve(
                    mappingRuleService.activeRules(), context);
            return new ActionExecutionReport.Failure(context.stepId(), decision.businessCode(),
                    decision.handlingConstraint(), decision.businessMessage(), deviceFault);
        }
        Integer clientCode = integer(execution.error(), "clientCode");
        if (clientCode != null) {
            ClientFaultCatalog.Decision decision = clientFaultCatalog.resolve(clientCode);
            return new ActionExecutionReport.Failure(context.stepId(), decision.businessCode(),
                    decision.handlingConstraint(), message(execution.error(), "下游客户端执行失败"), null);
        }
        return new ActionExecutionReport.Failure(context.stepId(), "ACTION.CLIENT.UNMAPPED_ERROR",
                HandlingConstraint.MANUAL_INTERVENTION,
                message(execution.error(), "下游未提供可识别的错误事实"), null);
    }

    private ErrorMappingContext mappingContext(ActionExecutionView execution) {
        JsonNode step = execution.lastStepEvent();
        JsonNode device = execution.error() == null ? null : execution.error().path("deviceFault");
        return new ErrorMappingContext(text(step, "stepId"), text(step, "operation"),
                text(device, "vendor"), text(device, "deviceType"), text(device, "code"),
                text(device, "message"));
    }

    private ActionExecutionReport.DeviceFault deviceFault(JsonNode error) {
        if (error == null || !error.isObject()) return null;
        JsonNode device = error.get("deviceFault");
        if (device == null || !device.isObject()) return null;
        return new ActionExecutionReport.DeviceFault(text(device, "vendor"),
                text(device, "deviceType"), text(device, "code"), text(device, "message"));
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.canConvertToInt() ? value.intValue() : null;
    }

    private String message(JsonNode error, String fallback) {
        String value = text(error, "message");
        return hasText(value) ? value : fallback;
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
