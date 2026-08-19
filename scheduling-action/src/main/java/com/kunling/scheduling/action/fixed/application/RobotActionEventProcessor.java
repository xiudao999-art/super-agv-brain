package com.kunling.scheduling.action.fixed.application;

import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEventListener;
import com.kunling.scheduling.action.robotbridge.application.RobotActionQuery;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionListener;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** 将传输层事件映射到持久化状态机，并在重连时只查询、不重放。 */
@Component
public class RobotActionEventProcessor implements RobotActionEventListener, RobotSessionListener {

    private static final Logger log = LoggerFactory.getLogger(RobotActionEventProcessor.class);

    private final RobotActionExecutionStore executionStore;
    private final ObjectProvider<RobotActionTransport> transportProvider;
    private final Clock clock;

    @Autowired
    public RobotActionEventProcessor(RobotActionExecutionStore executionStore,
                                     ObjectProvider<RobotActionTransport> transportProvider) {
        this(executionStore, transportProvider, Clock.systemUTC());
    }

    RobotActionEventProcessor(RobotActionExecutionStore executionStore,
                              ObjectProvider<RobotActionTransport> transportProvider,
                              Clock clock) {
        this.executionStore = executionStore;
        this.transportProvider = transportProvider;
        this.clock = clock;
    }

    @Override
    public void onEvent(RobotActionEvent event) {
        executionStore.applyEvent(event);
    }

    @Override
    public void onDisconnected(RobotSessionView session) {
        java.util.List<com.kunling.scheduling.action.fixed.domain.RobotActionExecutionView> held =
                executionStore.holdActiveExecutionsForRobot(session.robotId(), "ROBOT_CONNECTION_LOST",
                "动作执行期间机器人连接中断，物理结果无法确认", clock.instant());
        if (!held.isEmpty()) {
            log.warn("机器人 {} 离线，{} 个未完成动作已进入 UNKNOWN_HOLD", session.robotId(), held.size());
        }
    }

    @Override
    public void onConnected(RobotSessionView session) {
        RobotActionTransport transport = transportProvider.getObject();
        for (com.kunling.scheduling.action.fixed.domain.RobotActionExecutionView execution
                : executionStore.findHeldExecutionsForRobot(session.robotId())) {
            try {
                // 查询结果只补充人工处置证据，状态机不会自动解除既有 HOLD。
                transport.query(new RobotActionQuery(session.robotId(), execution.actionInstanceId(),
                        execution.deviceCommandId()));
            } catch (RobotUnavailableException exception) {
                log.warn("机器人 {} 重连对账失败: actionInstanceId={}", session.robotId(),
                        execution.actionInstanceId());
            }
        }
    }
}
