package com.kunling.scheduling.workflow.order.infrastructure;

import lombok.Data;

@Data
public class OrderTaskCount {
    private Long orderId;
    private Integer taskCount;
    private Integer completedTaskCount;
}
