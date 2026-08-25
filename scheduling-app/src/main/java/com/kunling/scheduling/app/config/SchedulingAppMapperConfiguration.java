package com.kunling.scheduling.app.config;

import com.kunling.scheduling.app.mapper.OperationLogMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * scheduling-app 模块的 MyBatis Mapper 扫描配置。
 *
 * <p>其他业务模块均声明了各自的局部 {@link MapperScan}。因此应用层也必须显式声明扫描范围，
 * 避免 MyBatis 自动扫描在检测到局部配置后退让，导致应用层 Mapper 未注册。</p>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = OperationLogMapper.class)
public class SchedulingAppMapperConfiguration {
}
