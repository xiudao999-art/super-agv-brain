package com.kunling.scheduling.action.fixed.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** 服务重启时不重放已持久化但未终结的整包动作。 */
@Component
public class FixedActionInterruptedRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FixedActionInterruptedRecovery.class);
    private final RobotActionExecutionStore executionStore;
    private final Clock clock = Clock.systemUTC();

    public FixedActionInterruptedRecovery(RobotActionExecutionStore executionStore) {
        this.executionStore = executionStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        java.util.List<com.kunling.scheduling.action.fixed.domain.RobotActionExecutionView> held =
                executionStore.holdInterruptedExecutions("SCHEDULING_SERVICE_RESTARTED",
                "调度服务重启，无法证明动作包在中断期间的物理执行结果", clock.instant());
        if (!held.isEmpty()) {
            log.warn("服务启动时将 {} 个未完成固定动作转为 UNKNOWN_HOLD", held.size());
        }
    }
}
