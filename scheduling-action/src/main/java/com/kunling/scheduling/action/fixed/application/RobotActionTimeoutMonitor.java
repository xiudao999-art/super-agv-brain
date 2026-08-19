package com.kunling.scheduling.action.fixed.application;

import com.kunling.scheduling.action.config.ActionModuleDefaults;
import com.kunling.scheduling.action.robotbridge.application.RobotActionQuery;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** 超时只触发 HOLD 和查询取证，不会重放原动作包。 */
@Component
public class RobotActionTimeoutMonitor {

    private static final Logger log = LoggerFactory.getLogger(RobotActionTimeoutMonitor.class);
    private final RobotActionExecutionStore executionStore;
    private final RobotActionTransport transport;
    private final Clock clock = Clock.systemUTC();

    public RobotActionTimeoutMonitor(RobotActionExecutionStore executionStore, RobotActionTransport transport) {
        this.executionStore = executionStore;
        this.transport = transport;
    }

    @Scheduled(fixedDelay = ActionModuleDefaults.FIXED_ACTION_TIMEOUT_SCAN_INTERVAL_MS)
    public void holdTimedOutExecutions() {
        for (com.kunling.scheduling.action.fixed.domain.RobotActionExecutionView execution
                : executionStore.holdTimedOutExecutions(clock.instant())) {
            log.warn("动作执行超时并进入 UNKNOWN_HOLD: actionInstanceId={}", execution.actionInstanceId());
            try {
                if (transport.findSession(execution.robotId()).isPresent()) {
                    transport.query(new RobotActionQuery(execution.robotId(), execution.actionInstanceId(),
                            execution.deviceCommandId()));
                }
            } catch (RobotUnavailableException exception) {
                log.warn("超时动作查询取证失败: actionInstanceId={}", execution.actionInstanceId());
            }
        }
    }
}
