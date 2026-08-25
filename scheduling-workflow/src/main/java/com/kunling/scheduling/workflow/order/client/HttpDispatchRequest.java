package com.kunling.scheduling.workflow.order.client;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/** 通用HTTP调度参数，不包含任何订单业务字段。 */
@Data
@Builder
public class HttpDispatchRequest {
    private String url;
    @Builder.Default
    private HttpMethod method = HttpMethod.GET;
    @Builder.Default
    private Map<String, ?> query = Collections.emptyMap();
    @Builder.Default
    private Map<String, String> headers = Collections.emptyMap();
    private Object body;
    @Builder.Default
    private Duration connectTimeout = Duration.ofSeconds(5);
    @Builder.Default
    private Duration readTimeout = Duration.ofSeconds(15);
}
