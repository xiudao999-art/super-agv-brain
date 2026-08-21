package com.kunling.scheduling.agvflow;

import com.kunling.scheduling.agvflow.domain.entity.Flow;
import com.kunling.scheduling.agvflow.domain.entity.FlowTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = AgvFlowModuleConfiguration.class)
@EntityScan(basePackageClasses = {FlowTemplate.class, Flow.class})
@MapperScan("com.kunling.scheduling.agvflow.mapper")
public class AgvFlowModuleConfiguration {
}
