package com.kunling.scheduling.action.config;

import java.time.Duration;

/** 下一期旧 HTTP 原子动作适配器所需的只读参数；一期固定为关闭。 */
public final class UpstreamProperties {

    private final boolean enabled;
    private final String baseUrl;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final Duration pollInterval;
    private final Duration catalogRefreshInterval;

    public UpstreamProperties(boolean enabled, String baseUrl, Duration connectTimeout,
                              Duration requestTimeout, Duration pollInterval,
                              Duration catalogRefreshInterval) {
        this.enabled = enabled;
        this.baseUrl = trimTrailing(baseUrl);
        this.connectTimeout = connectTimeout == null
                ? ActionModuleDefaults.UPSTREAM_CONNECT_TIMEOUT : connectTimeout;
        this.requestTimeout = requestTimeout == null
                ? ActionModuleDefaults.UPSTREAM_REQUEST_TIMEOUT : requestTimeout;
        this.pollInterval = pollInterval == null
                ? ActionModuleDefaults.UPSTREAM_POLL_INTERVAL : pollInterval;
        this.catalogRefreshInterval = catalogRefreshInterval == null
                ? ActionModuleDefaults.UPSTREAM_CATALOG_REFRESH_INTERVAL : catalogRefreshInterval;
    }

    private static String trimTrailing(String value) {
        return value == null ? "" : value.replaceFirst("\\s+$", "");
    }

    public boolean enabled() {
        return enabled;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public Duration pollInterval() {
        return pollInterval;
    }

    public Duration catalogRefreshInterval() {
        return catalogRefreshInterval;
    }
}
