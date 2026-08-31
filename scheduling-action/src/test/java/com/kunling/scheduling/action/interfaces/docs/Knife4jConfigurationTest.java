package com.kunling.scheduling.action.interfaces.docs;

import com.kunling.scheduling.action.robotbridge.config.Knife4jConfiguration;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import org.junit.jupiter.api.Test;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class Knife4jConfigurationTest {

    private final Knife4jConfiguration configuration = new Knife4jConfiguration();

    @Test
    void exposesActionApiMetadataAndGroup() {
        OpenAPIDefinition metadata = Knife4jConfiguration.class.getAnnotation(OpenAPIDefinition.class);
        GroupedOpenApi actionGroup = configuration.actionApiGroup();

        assertThat(metadata).isNotNull();
        assertThat(metadata.info().title()).isEqualTo("坤灵调度系统 Action 接口");
        assertThat(metadata.info().version()).isEqualTo("动态完整动作包协议");
        assertThat(actionGroup.getGroup()).isEqualTo("action");
        assertThat(actionGroup.getDisplayName()).isEqualTo("Action 动作接口");
        assertThat(actionGroup.getPackagesToScan()).containsExactly("com.kunling.scheduling.action");
        assertThat(actionGroup.getPathsToMatch()).containsExactly("/api/**");
    }

    @Test
    void knife4jUiResourceExistsOnRuntimeClasspath() {
        assertThat(new ClassPathResource("META-INF/resources/doc.html").exists()).isTrue();
    }
}
