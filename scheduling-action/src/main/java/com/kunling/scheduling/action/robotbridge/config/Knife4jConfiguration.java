package com.kunling.scheduling.action.robotbridge.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Action 模块接口文档配置。
 *
 * <p>接口和字段的中文说明直接写在控制器及 DTO 上，此处仅保留文档元数据和模块分组。</p>
 */
@OpenAPIDefinition(info = @Info(
        title = "坤灵调度系统 Action 接口",
        description = "当前 Action 配置、设备联调参数、动态完整动作包及执行证据查询接口",
        version = "动态完整动作包协议"
))
@Configuration(proxyBeanMethods = false)
public class Knife4jConfiguration {

    @Bean
    public GroupedOpenApi actionApiGroup() {
        return GroupedOpenApi.builder()
                .group("action")
                .displayName("Action 动作接口")
                // 同时限定包和路径，避免其他业务模块的接口误入 Action 文档。
                .packagesToScan("com.kunling.scheduling.action")
                .pathsToMatch("/api/**")
                .build();
    }
}
