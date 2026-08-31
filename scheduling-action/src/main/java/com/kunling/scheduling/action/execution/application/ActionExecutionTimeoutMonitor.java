package com.kunling.scheduling.action.execution.application;

import com.kunling.scheduling.action.config.ActionModuleDefaults;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** 超时只在 Java 本地进入 UNKNOWN_HOLD，不查询或重放下游动作。 */
@Component
public class ActionExecutionTimeoutMonitor {
    private static final Logger log = LoggerFactory.getLogger(ActionExecutionTimeoutMonitor.class);
    private final ActionExecutionStore executionStore;
    private final ActionExecutionReportPublisher reportPublisher;
    private final Clock clock;

    @Autowired
    public ActionExecutionTimeoutMonitor(ActionExecutionStore executionStore,
                                         ActionExecutionReportPublisher reportPublisher) {
        this(executionStore, reportPublisher, Clock.systemUTC());
    }

    ActionExecutionTimeoutMonitor(ActionExecutionStore executionStore,
                                  ActionExecutionReportPublisher reportPublisher,
                                  Clock clock) {
        this.executionStore = executionStore;
        this.reportPublisher = reportPublisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = ActionModuleDefaults.ACTION_TIMEOUT_SCAN_INTERVAL_MS)
    public void holdTimedOutExecutions() {
        java.time.Instant occurredAt = clock.instant();
        for (ActionExecutionView execution : executionStore.holdTimedOutExecutions(occurredAt)) {
            log.warn("动作执行超时并进入 UNKNOWN_HOLD: actionInstanceId={}", execution.actionInstanceId());
            reportPublisher.publishLocalState(execution, occurredAt);
        }
    }
}
