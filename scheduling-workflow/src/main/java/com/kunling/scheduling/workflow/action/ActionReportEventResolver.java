package com.kunling.scheduling.workflow.action;

import com.kunling.scheduling.action.exceptionmapping.domain.HandlingConstraint;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;
import com.kunling.scheduling.action.execution.domain.ActionExecutionResult;
import org.springframework.stereotype.Component;

/**
 * 将 Action 最终事实转换为流程状态机事件。
 *
 * <p>Action 给出的 handlingConstraint 是安全处理上限；物理结果未知或部分完成时，
 * 流程引擎必须收紧为人工介入，不能直接重试。</p>
 */
@Component
public class ActionReportEventResolver {

    public String resolve(ActionExecutionReport report) {
        if (report == null) {
            throw new IllegalArgumentException("Action 最终报告不能为空");
        }
        if (report.result() == ActionExecutionResult.SUCCEEDED) {
            return "SUCCEEDED";
        }
        if (report.result() == ActionExecutionResult.UNKNOWN_HOLD
                || report.physicalOutcome() == PhysicalOutcome.UNKNOWN
                || report.physicalOutcome() == PhysicalOutcome.PARTIALLY_COMPLETED) {
            return HandlingConstraint.MANUAL_INTERVENTION.name();
        }
        if (report.failure() == null || report.failure().handlingConstraint() == null) {
            throw new IllegalArgumentException("失败报告缺少 handlingConstraint");
        }
        return report.failure().handlingConstraint().name();
    }
}
