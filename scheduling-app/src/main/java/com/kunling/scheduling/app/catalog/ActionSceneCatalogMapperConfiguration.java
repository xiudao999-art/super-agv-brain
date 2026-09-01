package com.kunling.scheduling.app.catalog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** 仅扫描业务场景目录的只读仓储，保持该领域与通用应用 Mapper 解耦。 */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = ActionSceneCatalogRepository.class)
public class ActionSceneCatalogMapperConfiguration {
}
