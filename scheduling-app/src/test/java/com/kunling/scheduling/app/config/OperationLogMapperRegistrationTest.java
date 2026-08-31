package com.kunling.scheduling.app.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.kunling.scheduling.app.mapper.OperationLogMapper;
import com.kunling.scheduling.app.mapper.ActionParameterSchemaMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import static org.assertj.core.api.Assertions.assertThat;

/** 确保应用层 MyBatis Mapper 不会因其他模块的局部扫描配置而遗漏。 */
class OperationLogMapperRegistrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    MybatisPlusAutoConfiguration.class))
            .withUserConfiguration(AppMapperConfigurationScanner.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:operation_log_registration;MODE=MySQL",
                    "spring.datasource.driver-class-name=org.h2.Driver");

    @Test
    void 应用层Mapper配置会注册全部Mapper() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OperationLogMapper.class);
            assertThat(context).hasSingleBean(ActionParameterSchemaMapper.class);
        });
    }

    /** 仅扫描本次验证所需的 Mapper 配置，保持反馈循环快速且确定。 */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = "com.kunling.scheduling.app.config",
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "com\\.kunling\\.scheduling\\.app\\.config\\..*MapperConfiguration"))
    static class AppMapperConfigurationScanner {
    }
}
