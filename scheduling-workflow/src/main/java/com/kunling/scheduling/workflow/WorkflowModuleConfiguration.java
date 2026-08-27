package com.kunling.scheduling.workflow;

import com.kunling.scheduling.workflow.order.client.NoopOrderPullClient;
import com.kunling.scheduling.workflow.order.client.OrderPullClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springdoc.core.GroupedOpenApi;

@Configuration
@ComponentScan(basePackageClasses = WorkflowModuleConfiguration.class)
@MapperScan({"com.kunling.scheduling.workflow.mapper", "com.kunling.scheduling.workflow.order.infrastructure"})
@EnableScheduling
@EnableConfigurationProperties(com.kunling.scheduling.workflow.order.config.OrderSyncProperties.class)
public class WorkflowModuleConfiguration {
    @Bean
    public GroupedOpenApi workflowApiGroup() {
        return GroupedOpenApi.builder()
                .group("workflow")
                .displayName("Workflow 工作流接口")
                .packagesToScan("com.kunling.scheduling.workflow.controller")
                .pathsToMatch("/api/**")
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(OrderPullClient.class)
    public OrderPullClient noopOrderPullClient() {
        return new NoopOrderPullClient();
    }
}
