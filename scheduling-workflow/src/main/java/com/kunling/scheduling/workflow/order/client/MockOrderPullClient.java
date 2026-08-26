package com.kunling.scheduling.workflow.order.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

/** 临时假数据客户端，用于联调订单同步、优先级调度和任务串行执行。 */
@Component
@ConditionalOnProperty(prefix = "kunling.workflow.order-sync", name = "mock-enabled",
        havingValue = "true", matchIfMissing = true)
public class MockOrderPullClient implements OrderPullClient {

    @Override
    public PullOrderResponse pull(String source, LocalDateTime updatedFrom, LocalDateTime updatedTo,
                                  int page, int pageSize) {
        if (page > 1) return new PullOrderResponse(Collections.emptyList(), false);

        LocalDateTime issuedAt = LocalDateTime.of(2026, 8, 25, 14, 0);
        PulledOrder high = order(source, "MES-ORDER-0001", 1, issuedAt,
                task(1, "移动到贴标机台", "FLOW-WH-LABEL", 7L),
                task(2, "单次取料", "FLOW-WH-FEED", 7L));
        PulledOrder medium = order(source, "MES-ORDER-0002", 2, issuedAt.plusMinutes(1),
                task(1, "移动到仓库位", "FLOW-WH-FEED", 7L),
                task(2, "单次放料", "FLOW-WH-LABEL", 7L));
        PulledOrder low = order(source, "MES-ORDER-0003", 3, issuedAt.plusMinutes(2),
                task(1, "移动到反应区", "FLOW-WH-REACTION", 7L),
                task(2, "复合取料", "FLOW-WH-REACTION", 7L),
                task(3, "协作臂归零", "FLOW-WH-REACTION", 7L));
        return new PullOrderResponse(Arrays.asList(high, medium, low), false);
    }

    private PulledOrder order(String source, String orderNo, int priority, LocalDateTime issuedAt,
                              PulledTask... tasks) {
        PulledOrder order = new PulledOrder();
        order.setSource(source);
        order.setUpstreamOrderNo(orderNo);
        order.setPriority(priority);
        order.setIssuedAt(issuedAt);
        order.setUpstreamUpdatedAt(issuedAt);
        order.setTasks(Arrays.asList(tasks));
        return order;
    }

    private PulledTask task(int seq, String name, String flowNumber, Long flowTemplateId) {
        PulledTask task = new PulledTask();
        task.setTaskSeq(seq);
        task.setTaskName(name);
        task.setFlowNumber(flowNumber);
        task.setFlowTemplateId(flowTemplateId);
        return task;
    }
}
