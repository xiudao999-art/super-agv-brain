package com.kunling.scheduling.action.execution.application;

import com.kunling.scheduling.action.config.ActionModuleDefaults;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.robotbridge.application.RobotActionQuery;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** 超时只进入 HOLD 并查询取证，不自动重放任何物理动作。 */
@Component
public class ActionExecutionTimeoutMonitor {
    private static final Logger log = LoggerFactory.getLogger(ActionExecutionTimeoutMonitor.class);
    private final ActionExecutionStore executionStore;
    private final RobotActionTransport transport;
    private final ActionExecutionReportPublisher reportPublisher;
    private final Clock clock;

    @Autowired
    public ActionExecutionTimeoutMonitor(ActionExecutionStore executionStore,
                                         RobotActionTransport transport,
                                         ActionExecutionReportPublisher reportPublisher) {
        this(executionStore, transport, reportPublisher, Clock.systemUTC());
    }

    ActionExecutionTimeoutMonitor(ActionExecutionStore executionStore,
                                  RobotActionTransport transport,
                                  ActionExecutionReportPublisher reportPublisher,
                                  Clock clock) {
        this.executionStore = executionStore;
        this.transport = transport;
        this.reportPublisher = reportPublisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = ActionModuleDefaults.ACTION_TIMEOUT_SCAN_INTERVAL_MS)
    public void holdTimedOutExecutions() {
        java.time.Instant occurredAt = clock.instant();
        for (ActionExecutionView execution : executionStore.holdTimedOutExecutions(occurredAt)) {
            log.warn("动作执行超时并进入 UNKNOWN_HOLD: actionInstanceId={}", execution.actionInstanceId());
            reportPublisher.publishLocalState(execution, occurredAt);
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
