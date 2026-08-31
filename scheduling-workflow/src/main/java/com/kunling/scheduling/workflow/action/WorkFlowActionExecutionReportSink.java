package com.kunling.scheduling.workflow.action;

import com.kunling.scheduling.action.execution.application.ActionExecutionReportSink;
import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;
import com.kunling.scheduling.action.execution.domain.ActionExecutionResult;
import com.kunling.scheduling.workflow.service.NodeStateTransitionRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 接收 Action 模块的执行事实，并将终态事件交给 AGV Flow 状态机。
 */
@Component
public class WorkFlowActionExecutionReportSink implements ActionExecutionReportSink {

    private static final Logger log = LoggerFactory.getLogger(WorkFlowActionExecutionReportSink.class);

    private final ObjectProvider<NodeStateTransitionRuleService> transitionRuleServiceProvider;

    public WorkFlowActionExecutionReportSink(
            ObjectProvider<NodeStateTransitionRuleService> transitionRuleServiceProvider) {
        this.transitionRuleServiceProvider = transitionRuleServiceProvider;
    }

    @Override
    public void accept(ActionExecutionReport report) {
        if (report == null) {
            log.error("收到空 Action 最终报告，不能推进流程");
            return;
        }
        log.info("收到回调状态的内容: {}",
                report.toString());
        if (report.actionInstanceId() == null || report.actionInstanceId().trim().isEmpty()) {
            log.error("Action 最终报告缺少 actionInstanceId，不能关联流程节点");
            return;
        }
        if (report.result() != ActionExecutionResult.SUCCEEDED
                && (report.failure() == null || report.failure().handlingConstraint() == null)) {
            log.error("失败的 Action 报告缺少处置类型，不能推进流程: actionInstanceId={}",
                    report.actionInstanceId());
            return;
        }
        transitionRuleServiceProvider.getObject().statusChanged(report);
    }
}
