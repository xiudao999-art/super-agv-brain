package com.kunling.scheduling.workflow.order.application;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderReadyEvent {
    private final Long orderId;
}
