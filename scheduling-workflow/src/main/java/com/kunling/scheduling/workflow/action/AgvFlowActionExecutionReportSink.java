package com.kunling.scheduling.workflow.action;

import com.kunling.scheduling.action.execution.application.ActionExecutionReportSink;
import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;

import com.kunling.scheduling.workflow.dto.StatusChangedDto;
import com.kunling.scheduling.workflow.service.NodeStateTransitionRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 接收 Action 模块的执行事实，并将终态事件交给 AGV Flow 状态机。
 */
@Component
public class AgvFlowActionExecutionReportSink implements ActionExecutionReportSink {

    private static final Logger log = LoggerFactory.getLogger(AgvFlowActionExecutionReportSink.class);

    private final NodeStateTransitionRuleService transitionRuleService;

    public AgvFlowActionExecutionReportSink(NodeStateTransitionRuleService transitionRuleService) {
        this.transitionRuleService = transitionRuleService;
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
        if (report.success()) {
            transition.setEventCode("SUCCEEDED");
        } else {
            transition.setEventCode(report.failure().handling().name());
        }
        transition.setWorkflowInstanceId(report.workflowInstanceId());
        transition.setWorkflowNodeInstanceId(report.workflowNodeInstanceId());
        transitionRuleService.statusChanged(transition);
    }
}
