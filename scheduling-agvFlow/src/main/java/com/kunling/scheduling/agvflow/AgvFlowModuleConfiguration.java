package com.kunling.scheduling.agvflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * AGV Flow 模块唯一装配入口。
 *
 * <p>主应用只导入本配置，不直接依赖模块内部实现。</p>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.kunling.scheduling.agvflow.mapper")
@ComponentScan(basePackageClasses = AgvFlowModuleConfiguration.class)
public class AgvFlowModuleConfiguration {
}
