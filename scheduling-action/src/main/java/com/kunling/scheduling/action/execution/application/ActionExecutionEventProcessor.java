package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEventListener;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionListener;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** 将下游事件写入唯一执行状态机；断线后本地安全收敛，重连不恢复旧动作。 */
@Component
public class ActionExecutionEventProcessor implements RobotActionEventListener, RobotSessionListener {
    private static final Logger log = LoggerFactory.getLogger(ActionExecutionEventProcessor.class);
    private final ActionExecutionStore executionStore;
    private final ActionExecutionReportPublisher reportPublisher;
    private final Clock clock;

    // 可注入 Clock 的构造器仅供测试使用，生产环境必须明确由 Spring 选择此入口。
    @Autowired
    public ActionExecutionEventProcessor(ActionExecutionStore executionStore,
                                         ActionExecutionReportPublisher reportPublisher) {
        this(executionStore, Clock.systemUTC(), reportPublisher);
    }

    ActionExecutionEventProcessor(ActionExecutionStore executionStore,
                                  Clock clock,
                                  ActionExecutionReportPublisher reportPublisher) {
        this.executionStore = executionStore;
        this.clock = clock;
        this.reportPublisher = reportPublisher;
    }

    @Override
    public void onEvent(RobotActionEvent event) {
        executionStore.applyEvent(event).ifPresent(execution -> {
            logProgress(execution, event);
            reportPublisher.publish(execution, event);
        });
    }

    /** 每个应用于活动执行的下游事件都输出一行，联调时可直接观察逐步骤执行过程。 */
    private void logProgress(ActionExecutionView execution, RobotActionEvent event) {
        JsonNode step = event.stepEvent();
        log.info("Action 执行进度: actionInstanceId={}, actionDefinitionId={}, robotId={}, actionState={}, " +
                        "sequence={}, stepEventType={}, stepId={}, operation={}, stepState={}, attempt={}, stepEvent={}",
                execution.actionInstanceId(), execution.actionDefinitionId(), execution.robotId(), execution.state(),
                event.sequence(), text(step, "eventType"), text(step, "stepId"),
                text(step, "operation"), text(step, "stepState"), integer(step, "attempt"),
                step == null ? "-" : step);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "-" : value.asText();
    }

    private String integer(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.canConvertToInt() ? "-" : String.valueOf(value.intValue());
    }

    @Override
    public void onDisconnected(RobotSessionView session) {
        Instant occurredAt = clock.instant();
        List<ActionExecutionView> held = executionStore.holdActiveExecutionsForRobot(
                session.robotId(), "ROBOT_CONNECTION_LOST",
                "动作执行期间机器人连接中断，物理结果无法确认", occurredAt);
        for (ActionExecutionView execution : held) {
            reportPublisher.publishLocalState(execution, occurredAt);
        }
        if (!held.isEmpty()) {
            log.warn("机器人 {} 离线，{} 个未完成动作进入 UNKNOWN_HOLD", session.robotId(), held.size());
        }
    }
}
