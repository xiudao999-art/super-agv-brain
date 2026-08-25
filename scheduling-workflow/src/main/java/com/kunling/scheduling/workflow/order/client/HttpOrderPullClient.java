package com.kunling.scheduling.workflow.order.client;

import com.kunling.scheduling.workflow.order.config.OrderSyncProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/** 使用客户HTTP接口拉取增量订单。客户响应体需与PullOrderResponse结构一致。 */
@Component
@ConditionalOnProperty(prefix = "kunling.workflow.order-sync", name = "endpoint")
public class HttpOrderPullClient implements OrderPullClient {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final HttpClientUtil http;
    private final OrderSyncProperties properties;

    public HttpOrderPullClient(HttpClientUtil http, OrderSyncProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    @Override
    public PullOrderResponse pull(String source, LocalDateTime updatedFrom, LocalDateTime updatedTo,
                                  int page, int pageSize) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("source", source);
        query.put("updatedFrom", TIME_FORMAT.format(updatedFrom));
        query.put("updatedTo", TIME_FORMAT.format(updatedTo));
        query.put("page", page);
        query.put("pageSize", pageSize);

        Map<String, String> headers = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(properties.getBearerToken())) {
            headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getBearerToken().trim());
        }
        HttpDispatchRequest request = HttpDispatchRequest.builder()
                .url(properties.getEndpoint())
                .method(HttpMethod.GET)
                .query(query)
                .headers(headers)
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .build();
        PullOrderResponse response = http.execute(request, PullOrderResponse.class);
        if (response == null) throw new IllegalStateException("客户订单接口响应体为空");
        return response;
    }
}
