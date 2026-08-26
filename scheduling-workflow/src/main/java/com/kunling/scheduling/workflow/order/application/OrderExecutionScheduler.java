package com.kunling.scheduling.workflow.order.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderExecutionScheduler {
    private final OrderTaskOrchestrationService orchestrationService;

    public OrderExecutionScheduler(OrderTaskOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @Scheduled(fixedDelayString = "${kunling.workflow.order-execution.fixed-delay-ms:120000}")
    public void dispatch() {
        try {
            orchestrationService.dispatchNext();
        } catch (RuntimeException exception) {
            log.error("订单任务定时调度失败", exception);
        }
    }
}
