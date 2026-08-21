package com.kunling.scheduling.action.execution.application;

import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEventListener;
import com.kunling.scheduling.action.robotbridge.application.RobotActionQuery;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionListener;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** 将下游事件写入唯一执行状态机；断线和重连只查询证据，从不重放动作包。 */
@Component
public class ActionExecutionEventProcessor implements RobotActionEventListener, RobotSessionListener {
    private static final Logger log = LoggerFactory.getLogger(ActionExecutionEventProcessor.class);
    private final ActionExecutionStore executionStore;
    private final ObjectProvider<RobotActionTransport> transportProvider;
    private final ActionExecutionReportPublisher reportPublisher;
    private final Clock clock;

    // 可注入 Clock 的构造器仅供测试使用，生产环境必须明确由 Spring 选择此入口。
    @Autowired
    public ActionExecutionEventProcessor(ActionExecutionStore executionStore,
                                         ObjectProvider<RobotActionTransport> transportProvider,
                                         ActionExecutionReportPublisher reportPublisher) {
        this(executionStore, transportProvider, Clock.systemUTC(), reportPublisher);
    }

    ActionExecutionEventProcessor(ActionExecutionStore executionStore,
                                  ObjectProvider<RobotActionTransport> transportProvider,
                                  Clock clock,
                                  ActionExecutionReportPublisher reportPublisher) {
        this.executionStore = executionStore;
        this.transportProvider = transportProvider;
        this.clock = clock;
        this.reportPublisher = reportPublisher;
    }

    @Override
    public void onEvent(RobotActionEvent event) {
        executionStore.applyEvent(event).ifPresent(execution -> reportPublisher.publish(execution, event));
    }

    @Override
    public void onDisconnected(RobotSessionView session) {
        Instant occurredAt = clock.instant();
        List<ActionExecutionView> held = executionStore.holdActiveExecutionsForRobot(
                session.robotId(), "ROBOT_CONNECTION_LOST",
                "动作执行期间机器人连接中断，物理结果无法确认", occurredAt);
        for (ActionExecutionView execution : held) {
            reportPublisher.publishLocalState(execution, "ROBOT_CONNECTION_LOST", occurredAt);
        }
        if (!held.isEmpty()) {
            log.warn("机器人 {} 离线，{} 个未完成动作进入 UNKNOWN_HOLD", session.robotId(), held.size());
        }
    }

    @Override
    public void onConnected(RobotSessionView session) {
        RobotActionTransport transport = transportProvider.getObject();
        for (ActionExecutionView execution : executionStore.findHeldExecutionsForRobot(session.robotId())) {
            try {
                transport.query(new RobotActionQuery(session.robotId(), execution.actionInstanceId(),
                        execution.deviceCommandId()));
            } catch (RobotUnavailableException exception) {
                log.warn("机器人 {} 重连查询失败: actionInstanceId={}",
                        session.robotId(), execution.actionInstanceId());
            }
        }
    }
}
