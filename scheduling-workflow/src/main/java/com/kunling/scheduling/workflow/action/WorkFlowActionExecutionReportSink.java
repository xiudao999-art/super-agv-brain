package com.kunling.scheduling.workflow.action;

import com.kunling.scheduling.action.execution.application.ActionExecutionReportSink;
import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;


import com.kunling.scheduling.workflow.dto.StatusChangedDto;
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
        log.info("收到回调状态的内容: {}",
                report.toString());
        if (report.workflowNodeInstanceId() == null
                || report.workflowNodeInstanceId().trim().isEmpty()) {
            log.info("Action 报告未关联流程节点，仅记录结果: actionInstanceId={}",
                    report.actionInstanceId());
            return;
        }
        if (!report.success() && (report.failure() == null
                || report.failure().handling() == null)) {
            log.error("失败的 Action 报告缺少处置类型，不能推进流程: actionInstanceId={}",
                    report.actionInstanceId());
            return;
        }

        StatusChangedDto transition = new StatusChangedDto();
        transition.setSuccess(report.success());
        transition.setActionInstanceId(report.actionInstanceId());
        transition.setActionKey(report.actionKey());
        transition.setPhysicalOutcome(report.physicalOutcome() == null ? null : report.physicalOutcome().name());
        if (report.success()) {
            transition.setEventCode("SUCCEEDED");
        } else {
            transition.setEventCode(report.failure().handling().name());
            transition.setBusinessCode(report.failure().businessCode());
            transition.setReasonCode(report.failure().reasonCode());
        }
        transition.setWorkflowInstanceId(report.workflowInstanceId());
        transition.setWorkflowNodeInstanceId(report.workflowNodeInstanceId());
        transitionRuleServiceProvider.getObject().statusChanged(transition);
    }
}
