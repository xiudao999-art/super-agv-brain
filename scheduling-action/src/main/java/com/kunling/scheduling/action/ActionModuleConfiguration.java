package com.kunling.scheduling.action;

import com.kunling.scheduling.action.capability.infrastructure.AtomicCapabilityEntity;
import com.kunling.scheduling.action.capability.infrastructure.AtomicCapabilityRepository;
import com.kunling.scheduling.action.config.ActionProperties;
import com.kunling.scheduling.action.config.UpstreamProperties;
import com.kunling.scheduling.action.definition.infrastructure.ActionDraftEntity;
import com.kunling.scheduling.action.definition.infrastructure.ActionDraftRepository;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionEntity;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionRepository;
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
@ComponentScan(basePackageClasses = ActionModuleConfiguration.class)
@EntityScan(basePackageClasses = {
        AtomicCapabilityEntity.class,
        ActionDraftEntity.class,
        ActionExecutionEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        AtomicCapabilityRepository.class,
        ActionDraftRepository.class,
        ActionExecutionRepository.class
})
@EnableConfigurationProperties({ActionProperties.class, UpstreamProperties.class})
@EnableScheduling
public class ActionModuleConfiguration {
}
