package com.kunling.scheduling.action;

import com.kunling.scheduling.action.config.ActionProperties;
import com.kunling.scheduling.action.config.UpstreamProperties;
import com.kunling.scheduling.action.robotbridge.config.RobotBridgeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ActionModuleConfigurationTest {

    @Test
    void businessDefaultsStayInModuleWhileTcpDeploymentSettingsAreExternallyConfigurable() {
        assertThat(AnnotatedElementUtils.findMergedAnnotation(ActionProperties.class, ConfigurationProperties.class))
                .isNull();
        assertThat(AnnotatedElementUtils.findMergedAnnotation(UpstreamProperties.class, ConfigurationProperties.class))
                .isNull();
        ConfigurationProperties robotBridgeBinding = AnnotatedElementUtils.findMergedAnnotation(
                RobotBridgeProperties.class,
                ConfigurationProperties.class
        );
        assertThat(robotBridgeBinding).isNotNull();
        assertThat(robotBridgeBinding.prefix()).isEqualTo("kunling.action.robot-bridge");

        EnableConfigurationProperties enabledProperties = AnnotatedElementUtils.findMergedAnnotation(
                ActionModuleConfiguration.class,
                EnableConfigurationProperties.class
        );
        assertThat(enabledProperties).isNotNull();
        assertThat(enabledProperties.value()).contains(RobotBridgeProperties.class);

        ActionProperties actionProperties = new ActionProperties(null);
        assertThat(actionProperties.compiler().maximumActionDepth()).isEqualTo(8);
        assertThat(actionProperties.compiler().maximumCompiledNodes()).isEqualTo(500);
        assertThat(actionProperties.compiler().maximumForEachIterations()).isEqualTo(6);
        assertThat(actionProperties.compiler().maximumPlanBytes()).isEqualTo(524_288);

        UpstreamProperties upstreamProperties = new UpstreamProperties(false, null, null, null, null, null);
        assertThat(upstreamProperties.enabled()).isFalse();

        RobotBridgeProperties robotBridgeProperties = new RobotBridgeProperties(
                true, null, 8080, 0, 0, 0, null
        );
        assertThat(robotBridgeProperties.acceptedActionTypes())
                .containsExactlyElementsOf(Arrays.asList("MOVE", "ARM.HOME", "ARM.PICK", "ARM.PLACE"));
    }
}
