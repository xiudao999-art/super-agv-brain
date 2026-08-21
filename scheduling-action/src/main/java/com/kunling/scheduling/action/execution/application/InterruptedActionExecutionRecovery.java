package com.kunling.scheduling.action.execution.application;

import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** 服务重启后把未完成执行转为 UNKNOWN_HOLD，禁止自动重发原动作包。 */
@Component
public class InterruptedActionExecutionRecovery implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(InterruptedActionExecutionRecovery.class);
    private final ActionExecutionStore executionStore;
    private final ActionExecutionReportPublisher reportPublisher;
    private final Clock clock;

    @Autowired
    public InterruptedActionExecutionRecovery(ActionExecutionStore executionStore,
                                               ActionExecutionReportPublisher reportPublisher) {
        this(executionStore, reportPublisher, Clock.systemUTC());
    }

    InterruptedActionExecutionRecovery(ActionExecutionStore executionStore,
                                       ActionExecutionReportPublisher reportPublisher,
                                       Clock clock) {
        this.executionStore = executionStore;
        this.reportPublisher = reportPublisher;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant occurredAt = clock.instant();
        List<ActionExecutionView> held =
                executionStore.holdInterruptedExecutions("SCHEDULING_SERVICE_RESTARTED",
                        "调度服务重启，无法证明中断期间的物理执行结果", occurredAt);
        for (ActionExecutionView execution : held) {
            reportPublisher.publishLocalState(execution, "SCHEDULING_SERVICE_RESTARTED", occurredAt);
        }
        int count = held.size();
        if (count > 0) {
            log.warn("服务启动时将 {} 个未完成动作转为 UNKNOWN_HOLD", count);
        }
    }
}
