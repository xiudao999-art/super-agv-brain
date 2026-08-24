package com.kunling.scheduling.app.config;

import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * scheduling-app 对外管理接口的 OpenAPI 分组配置。
 *
 * <p>该分组只负责应用层资源配置接口，避免与 AGV 流程编排接口混在同一个 Knife4j 模块中。</p>
 */
@Configuration(proxyBeanMethods = false)
public class SchedulingAppOpenApiConfiguration {

    @Bean
    public GroupedOpenApi resourceConfigApiGroup() {
        return GroupedOpenApi.builder()
                .group("resource-config")
                .displayName("系统接口")
                // Knife4j 4.4.0 要求分组显式提供非空包扫描列表。
                .packagesToScan("com.kunling.scheduling.app.controller")
                .pathsToMatch(
                        "/api/lab-spaces/**", "/api/lab-configs/**",
                        "/api/files/**",
                        "/locations/**", "/locationTypes/**",
                        "/carriers/**", "/carrierTypes/**")
                .build();
    }
}
