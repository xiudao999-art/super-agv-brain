package com.kunling.scheduling.agvflow;

import com.kunling.scheduling.agvflow.domain.entity.FlowTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AgvFlowModuleConfiguration.class)
@EntityScan(basePackageClasses = {FlowTemplate.class})
@MapperScan("com.kunling.scheduling.agvflow.mapper")
public class AgvFlowModuleConfiguration {

    @Bean
    public GroupedOpenApi agvFlowApiGroup() {
        return GroupedOpenApi.builder()
                .group("agv-flow")
                .displayName("AGV Flow 接口")
                .packagesToScan("com.kunling.scheduling.agvflow")
                .pathsToMatch("/**")
                .build();
    }
}
