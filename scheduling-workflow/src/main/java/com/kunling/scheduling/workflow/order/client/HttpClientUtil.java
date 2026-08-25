package com.kunling.scheduling.workflow.order.client;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/** 当前模块统一的JSON HTTP调用工具。 */
@Component
public class HttpClientUtil {
    private final RestTemplateBuilder builder;

    public HttpClientUtil(RestTemplateBuilder builder) {
        this.builder = builder;
    }

    public <T> T get(String url, Map<String, ?> query, Map<String, String> headers,
                     Class<T> responseType, Duration connectTimeout, Duration readTimeout) {
        return exchange(url, HttpMethod.GET, query, headers, null, responseType,
                connectTimeout, readTimeout);
    }

    /** 通用HTTP调度，适用于普通Java响应类型。 */
    public <T> T execute(HttpDispatchRequest request, Class<T> responseType) {
        validate(request);
        return exchange(request.getUrl(), request.getMethod(), request.getQuery(), request.getHeaders(),
                request.getBody(), responseType, request.getConnectTimeout(), request.getReadTimeout());
    }

    /** 通用HTTP调度，支持List、Map及嵌套泛型响应。 */
    public <T> T execute(HttpDispatchRequest request, ParameterizedTypeReference<T> responseType) {
        validate(request);
        RestTemplate restTemplate = restTemplate(request.getConnectTimeout(), request.getReadTimeout());
        String requestUrl = buildUrl(request.getUrl(), request.getQuery());
        HttpEntity<Object> entity = new HttpEntity<>(request.getBody(), buildHeaders(request.getHeaders()));
        try {
            ResponseEntity<T> response = restTemplate.exchange(
                    requestUrl, request.getMethod(), entity, responseType);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("HTTP调用失败，status=" + response.getStatusCodeValue()
                        + ", url=" + requestUrl);
            }
            return response.getBody();
        } catch (RestClientException exception) {
            throw new IllegalStateException("HTTP调用异常，method=" + request.getMethod()
                    + ", url=" + requestUrl, exception);
        }
    }

    public <B, T> T post(String url, B body, Map<String, String> headers,
                         Class<T> responseType, Duration connectTimeout, Duration readTimeout) {
        return exchange(url, HttpMethod.POST, Collections.emptyMap(), headers, body, responseType,
                connectTimeout, readTimeout);
    }

    public <T> T exchange(String url, HttpMethod method, Map<String, ?> query,
                          Map<String, String> headers, Object body, Class<T> responseType,
                          Duration connectTimeout, Duration readTimeout) {
        RestTemplate restTemplate = restTemplate(connectTimeout, readTimeout);
        String requestUrl = buildUrl(url, query);
        HttpEntity<Object> entity = new HttpEntity<>(body, buildHeaders(headers));
        try {
            ResponseEntity<T> response = restTemplate.exchange(requestUrl, method, entity, responseType);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("HTTP调用失败，status=" + response.getStatusCodeValue()
                        + ", url=" + requestUrl);
            }
            return response.getBody();
        } catch (RestClientException exception) {
            throw new IllegalStateException("HTTP调用异常，method=" + method + ", url=" + requestUrl, exception);
        }
    }

    private RestTemplate restTemplate(Duration connectTimeout, Duration readTimeout) {
        return builder.setConnectTimeout(connectTimeout).setReadTimeout(readTimeout).build();
    }

    private void validate(HttpDispatchRequest request) {
        if (request == null) throw new IllegalArgumentException("HTTP调度参数不能为空");
        if (request.getMethod() == null) throw new IllegalArgumentException("HTTP方法不能为空");
        if (request.getConnectTimeout() == null || request.getReadTimeout() == null) {
            throw new IllegalArgumentException("HTTP连接和读取超时不能为空");
        }
    }

    private String buildUrl(String url, Map<String, ?> query) {
        if (url == null || url.trim().isEmpty()) throw new IllegalArgumentException("HTTP请求URL不能为空");
        UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(url);
        if (query != null) {
            query.forEach((key, value) -> {
                if (value != null) uri.queryParam(key, value);
            });
        }
        return uri.build().encode().toUriString();
    }

    private HttpHeaders buildHeaders(Map<String, String> values) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (values != null) values.forEach(headers::set);
        return headers;
    }
}
