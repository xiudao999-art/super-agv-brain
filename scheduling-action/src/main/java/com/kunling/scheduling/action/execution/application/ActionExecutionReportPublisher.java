package com.kunling.scheduling.action.execution.application;

import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** 保存动作事实后再向所有本地接收方交付报告；接收失败不得回滚设备事实。 */
@Component
public class ActionExecutionReportPublisher {
    private static final Logger log = LoggerFactory.getLogger(ActionExecutionReportPublisher.class);
    private final ActionExecutionReportMapper mapper;
    private final List<ActionExecutionReportSink> sinks;

    public ActionExecutionReportPublisher(ActionExecutionReportMapper mapper,
                                          List<ActionExecutionReportSink> sinks) {
        this.mapper = mapper;
        this.sinks = sinks;
    }

    public void publish(ActionExecutionView execution, RobotActionEvent event) {
        mapper.fromTerminalRobotEvent(execution, event).ifPresent(this::publish);
    }

    public void publishLocalState(ActionExecutionView execution, Instant occurredAt) {
        publish(mapper.fromLocalState(execution, occurredAt));
    }

    public void publish(ActionExecutionReport report) {
        log.info("Action 最终结果: actionInstanceId={}, workflowInstanceId={}, workflowNodeInstanceId={}, " +
                        "actionKey={}, robotId={}, success={}, physicalResultKnown={}, physicalOutcome={}, " +
                        "completedAt={}, businessCode={}, reasonCode={}, businessDisposition={}, " +
                        "output={}, failure={}",
                report.actionInstanceId(), report.workflowInstanceId(), report.workflowNodeInstanceId(),
                report.actionKey(), report.robotId(), report.success(), report.physicalResultKnown(),
                report.physicalOutcome(), report.completedAt(),
                report.failure() == null ? "-" : report.failure().businessCode(),
                report.failure() == null ? "-" : report.failure().reasonCode(),
                report.failure() == null ? "-" : report.failure().businessDisposition(),
                report.output(), report.failure());
        for (ActionExecutionReportSink sink : sinks) {
            try {
                sink.accept(report);
            } catch (RuntimeException exception) {
                // 设备事件已经持久化。执行引擎接收失败只记录，不得伪造设备执行失败或回滚状态。
                log.error("Action 最终结果交付失败: actionInstanceId={}",
                        report.actionInstanceId(), exception);
            }
        }
    }
}
