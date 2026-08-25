package com.kunling.scheduling.workflow.order.client;

import java.time.LocalDateTime;
import java.util.Collections;

public class NoopOrderPullClient implements OrderPullClient {
    @Override
    public PullOrderResponse pull(String source, LocalDateTime updatedFrom, LocalDateTime updatedTo,
                                  int page, int pageSize) {
        return new PullOrderResponse(Collections.emptyList(), false);
    }
}
