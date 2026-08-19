package com.kunling.scheduling.action.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "kunling.action.upstream")
public record UpstreamProperties(
        boolean enabled,
        String baseUrl,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration pollInterval,
        Duration catalogRefreshInterval) {

    public UpstreamProperties {
        baseUrl = baseUrl == null ? "" : baseUrl.stripTrailing();
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout;
        pollInterval = pollInterval == null ? Duration.ofMillis(200) : pollInterval;
        catalogRefreshInterval = catalogRefreshInterval == null ? Duration.ofMinutes(5) : catalogRefreshInterval;
    }
}
