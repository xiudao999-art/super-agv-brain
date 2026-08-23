package com.kunling.scheduling.workflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses = WorkflowModuleConfiguration.class)
@MapperScan("com.kunling.scheduling.workflow.mapper")
public class WorkflowModuleConfiguration {
}
