package com.kunling.scheduling.workflow.order.client;

import java.time.LocalDateTime;

public interface OrderPullClient {
    PullOrderResponse pull(String source, LocalDateTime updatedFrom, LocalDateTime updatedTo,
                           int page, int pageSize);
}
