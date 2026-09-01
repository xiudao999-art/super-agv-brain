package com.kunling.scheduling.workflow.order.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.workflow.order.config.OrderSyncProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 使用客户HTTP接口拉取增量订单。客户响应体需与PullOrderResponse结构一致。 */
@Component
@ConditionalOnProperty(prefix = "kunling.workflow.order-sync", name = "mock-enabled", havingValue = "false")
public class HttpOrderPullClient implements OrderPullClient {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final HttpClientUtil http;
    private final OrderSyncProperties properties;
    private final ObjectMapper objectMapper;

    public HttpOrderPullClient(HttpClientUtil http, OrderSyncProperties properties, ObjectMapper objectMapper) {
        this.http = http;
        this.properties = properties;
        this.objectMapper = objectMapper;
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
        JsonNode response = http.execute(request, JsonNode.class);
        if (response == null || response.isNull()) throw new IllegalStateException("客户订单接口响应体为空");
        return normalize(response);
    }

    /** 兼容单订单、订单数组以及{orders:[], hasNext:false}/{data:...}包装。 */
    private PullOrderResponse normalize(JsonNode response) {
        JsonNode payload = response.has("data") ? response.path("data") : response;
        boolean hasNext = response.path("hasNext").asBoolean(payload.path("hasNext").asBoolean(false));
        JsonNode ordersNode = payload.has("orders") ? payload.path("orders") : payload;
        List<PulledOrder> orders = new ArrayList<>();
        try {
            if (ordersNode.isArray()) {
                for (JsonNode item : ordersNode) orders.add(objectMapper.treeToValue(item, PulledOrder.class));
            } else if (ordersNode.isObject()
                    && (ordersNode.has("upperId") || ordersNode.has("upstreamOrderNo"))) {
                orders.add(objectMapper.treeToValue(ordersNode, PulledOrder.class));
            } else if (!ordersNode.isNull() && !ordersNode.isMissingNode()) {
                throw new IllegalArgumentException("客户订单接口响应格式不正确");
            }
            return new PullOrderResponse(orders, hasNext);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("客户订单接口响应解析失败", exception);
        }
    }
}
