package com.kunling.scheduling.workflow.action;

import com.kunling.scheduling.action.execution.application.ActionExecutionReportSink;
import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;
import com.kunling.scheduling.workflow.service.FlowControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Action物理执行成功后自动推进对应的Flowable receiveTask。 */
@Component
public class WorkflowActionExecutionReportSink implements ActionExecutionReportSink {
    private static final Logger log = LoggerFactory.getLogger(WorkflowActionExecutionReportSink.class);
    private final FlowControlService flowControlService;

    public WorkflowActionExecutionReportSink(@Lazy FlowControlService flowControlService) {
        this.flowControlService = flowControlService;
    }

    @Override
    public void accept(ActionExecutionReport report) {
        if (!report.success()) {
            return;
        }
        if (blank(report.workflowNodeInstanceId()) || blank(report.actionInstanceId())
                || blank(report.workflowInstanceId())) {
            log.warn("成功Action报告缺少流程关联参数，无法推进: actionInstanceId={}", report.actionInstanceId());
            return;
        }
        flowControlService.processCallback(report.workflowNodeInstanceId(), report.actionInstanceId(),
                report.workflowInstanceId());
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
