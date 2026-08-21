package com.kunling.scheduling.action.config;

import com.kunling.scheduling.action.ActionModulePackage;
import com.kunling.scheduling.action.commissioning.infrastructure.ActionParameterSetEntity;
import com.kunling.scheduling.action.commissioning.infrastructure.ActionParameterSetRepository;
import com.kunling.scheduling.action.definition.infrastructure.ActionDefinitionEntity;
import com.kunling.scheduling.action.definition.infrastructure.ActionDefinitionRepository;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionEventEntity;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionEventRepository;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionEntity;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionRepository;
import com.kunling.scheduling.action.robotbridge.config.RobotBridgeProperties;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Action 模块唯一装配入口。
 *
 * <p>主应用只需要导入本配置，不需要了解模块内部的领域对象、仓储或上游适配实现。</p>
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = ActionModulePackage.class)
@EntityScan(basePackageClasses = {
        ActionDefinitionEntity.class,
        ActionParameterSetEntity.class,
        ActionExecutionEntity.class,
        ActionExecutionEventEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        ActionDefinitionRepository.class,
        ActionParameterSetRepository.class,
        ActionExecutionRepository.class,
        ActionExecutionEventRepository.class
})
@EnableScheduling
@EnableConfigurationProperties(RobotBridgeProperties.class)
public class ActionModuleConfiguration {
}
