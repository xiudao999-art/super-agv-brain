package com.kunling.scheduling.workflow.order.application;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderSyncResult {
    private int pulled;
    private int created;
    private int updated;
}
