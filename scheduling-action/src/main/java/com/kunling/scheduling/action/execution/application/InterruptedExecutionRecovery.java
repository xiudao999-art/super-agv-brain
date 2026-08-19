package com.kunling.scheduling.action.execution.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class InterruptedExecutionRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InterruptedExecutionRecovery.class);
    private final ExecutionStateService stateService;

    public InterruptedExecutionRecovery(ExecutionStateService stateService) {
        this.stateService = stateService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 服务进程丢失期间无法证明机器人没有继续运动，因此不能自动恢复或重放原子命令。
        int recovered = stateService.holdInterruptedExecutions();
        if (recovered > 0) {
            log.warn("服务启动时将 {} 个未完成 Action 转为 UNKNOWN_HOLD", recovered);
        }
    }
}
