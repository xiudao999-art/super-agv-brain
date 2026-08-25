package com.kunling.scheduling.workflow.order.application;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderPersistResult {
    private Long orderId;
    private boolean created;
    private boolean newTasks;
}
