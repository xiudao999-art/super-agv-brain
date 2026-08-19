package com.kunling.scheduling.action;

import com.kunling.scheduling.action.config.ActionProperties;
import com.kunling.scheduling.action.config.UpstreamProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.annotation.AnnotatedElementUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ActionModuleConfigurationTest {

    @Test
    void moduleConfigurationUsesActionNamespace() {
        assertConfigurationPrefix(ActionProperties.class, "kunling.action");
        assertConfigurationPrefix(UpstreamProperties.class, "kunling.action.upstream");
    }

    private void assertConfigurationPrefix(Class<?> propertiesType, String expectedPrefix) {
        ConfigurationProperties annotation = AnnotatedElementUtils.findMergedAnnotation(
                propertiesType,
                ConfigurationProperties.class
        );

        assertThat(annotation).isNotNull();
        assertThat(annotation.prefix()).isEqualTo(expectedPrefix);
    }
}
