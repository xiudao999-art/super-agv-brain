package com.kunling.scheduling.action.interfaces.docs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

import static com.kunling.scheduling.action.interfaces.docs.ActionApiDocumentation.TAG_ACTION_MANAGEMENT;
import static com.kunling.scheduling.action.interfaces.docs.ActionApiDocumentation.TAG_CAPABILITY;
import static com.kunling.scheduling.action.interfaces.docs.ActionApiDocumentation.TAG_DYNAMIC_EXECUTION;
import static com.kunling.scheduling.action.interfaces.docs.ActionApiDocumentation.TAG_FIXED_ACTION;
import static com.kunling.scheduling.action.interfaces.docs.ActionApiDocumentation.TAG_ROBOT_SESSION;

/**
 * Action 模块接口文档配置。
 *
 * <p>文档元数据和接口分组跟随 Action 模块维护，主应用只负责是否启用以及暴露路径。</p>
 */
@Configuration(proxyBeanMethods = false)
public class Knife4jConfiguration {

    @Bean
    public OpenAPI actionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("坤灵调度系统动作接口文档")
                        .description("提供固定动作下发、机器人连接查询、动作配置管理及执行状态查询能力")
                        .version("一期 v1"))
                .tags(Arrays.asList(
                        new Tag().name(TAG_FIXED_ACTION).description("下发一期固定动作包并查询或核对执行状态"),
                        new Tag().name(TAG_ROBOT_SESSION).description("查询主动连接到调度系统的机器人会话"),
                        new Tag().name(TAG_ACTION_MANAGEMENT).description("维护动作草稿、发布版本及全局组合动作目录"),
                        new Tag().name(TAG_CAPABILITY).description("查询并同步上游原子能力目录"),
                        new Tag().name(TAG_DYNAMIC_EXECUTION).description("二期动态动作执行接口，默认关闭")
                ));
    }

    @Bean
    public GroupedOpenApi actionApiGroup() {
        return GroupedOpenApi.builder()
                .group("action")
                .displayName("动作接口")
                // 同时限定包和路径，避免其他业务模块的接口误入 Action 文档。
                .packagesToScan("com.kunling.scheduling.action")
                .pathsToMatch("/api/**")
                .build();
    }
}
