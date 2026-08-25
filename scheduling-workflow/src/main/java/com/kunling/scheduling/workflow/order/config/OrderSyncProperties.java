package com.kunling.scheduling.workflow.order.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "kunling.workflow.order-sync")
public class OrderSyncProperties {
    private boolean enabled = true;
    private long fixedDelayMs = 10000L;
    private int pageSize = 100;
    private Duration overlap = Duration.ofMinutes(1);
    private List<String> sources = new ArrayList<>(Collections.singletonList("MES"));
    /** 客户订单查询接口完整URL；为空时使用NoopOrderPullClient。 */
    private String endpoint;
    /** 可选的Bearer Token。 */
    private String bearerToken;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(15);
}
