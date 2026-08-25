package com.kunling.scheduling.workflow.order.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PullOrderResponse {
    private List<PulledOrder> orders = new ArrayList<>();
    private boolean hasNext;
}
