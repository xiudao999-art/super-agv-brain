package com.kunling.scheduling.action.execution.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** 服务重启后把未完成执行转为 UNKNOWN_HOLD，禁止自动重发原动作包。 */
@Component
public class InterruptedActionExecutionRecovery implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(InterruptedActionExecutionRecovery.class);
    private final ActionExecutionStore executionStore;
    private final Clock clock = Clock.systemUTC();

    public InterruptedActionExecutionRecovery(ActionExecutionStore executionStore) {
        this.executionStore = executionStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        int count = executionStore.holdInterruptedExecutions("SCHEDULING_SERVICE_RESTARTED",
                "调度服务重启，无法证明中断期间的物理执行结果", clock.instant()).size();
        if (count > 0) {
            log.warn("服务启动时将 {} 个未完成动作转为 UNKNOWN_HOLD", count);
        }
    }
}
