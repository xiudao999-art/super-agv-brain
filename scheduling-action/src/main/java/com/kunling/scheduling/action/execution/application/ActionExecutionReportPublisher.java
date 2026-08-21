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
        publish(mapper.fromRobotEvent(execution, event));
    }

    public void publishLocalState(ActionExecutionView execution, String reasonCode, Instant occurredAt) {
        publish(mapper.fromLocalState(execution, reasonCode, occurredAt));
    }

    public void publish(ActionExecutionReport report) {
        for (ActionExecutionReportSink sink : sinks) {
            try {
                sink.accept(report);
            } catch (RuntimeException exception) {
                // 设备事件已经持久化。执行引擎接收失败只记录，不得伪造设备执行失败或回滚状态。
                log.error("Action 执行报告交付失败: actionInstanceId={}, eventId={}",
                        report.correlation().actionInstanceId(), report.eventId(), exception);
            }
        }
    }
}
