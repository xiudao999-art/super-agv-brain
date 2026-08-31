package com.kunling.scheduling.action.robotbridge;

import com.kunling.scheduling.action.robotbridge.config.RobotBridgeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RobotBridgePropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(BindingConfiguration.class);

    @Test
    void bindsTcpListenerSettingsFromDeploymentConfiguration() {
        contextRunner.withPropertyValues(
                        "kunling.action.robot-bridge.enabled=false",
                        "kunling.action.robot-bridge.bind-address=127.0.0.1",
                        "kunling.action.robot-bridge.port=18080"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RobotBridgeProperties properties = context.getBean(RobotBridgeProperties.class);
                    assertThat(properties.enabled()).isFalse();
                    assertThat(properties.bindAddress()).isEqualTo("127.0.0.1");
                    assertThat(properties.port()).isEqualTo(18080);
                    // 协议安全边界仍由 Action 模块维护，不因部署环境配置而漂移。
                    assertThat(properties.leaseMs()).isEqualTo(30000);
                    assertThat(properties.heartbeatIntervalMs()).isEqualTo(10000);
                    assertThat(properties.maximumMessageBytes()).isEqualTo(1048576);
                });
    }

    @Test
    void rejectsInvalidTcpListenerPortDuringStartup() {
        contextRunner.withPropertyValues("kunling.action.robot-bridge.port=70000")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("robot-bridge.port 必须在 0 到 65535 之间");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RobotBridgeProperties.class)
    static class BindingConfiguration {
    }
}
