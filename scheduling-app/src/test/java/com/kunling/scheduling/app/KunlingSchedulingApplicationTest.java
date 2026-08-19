package com.kunling.scheduling.app;

import com.kunling.scheduling.action.ActionModuleConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.env.YamlPropertySourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class KunlingSchedulingApplicationTest {

    @Test
    void mainApplicationExplicitlyLoadsActionModule() {
        Import moduleImport = AnnotatedElementUtils.findMergedAnnotation(
                KunlingSchedulingApplication.class,
                Import.class
        );

        assertThat(moduleImport).isNotNull();
        assertThat(moduleImport.value()).containsExactly(ActionModuleConfiguration.class);
    }

    @Test
    void applicationUsesSchedulingServiceName() throws Exception {
        java.util.List<org.springframework.core.env.PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(
                "application",
                new ClassPathResource("application.yml")
        );

        assertThat(propertySources)
                .anySatisfy(source -> assertThat(source.getProperty("spring.application.name"))
                        .isEqualTo("kunling-scheduling"));
    }

    @Test
    void yamlContainsOnlyDeploymentConfigurationAndUsesDirectDatasourceCredentials() throws Exception {
        java.util.List<org.springframework.core.env.PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(
                "application",
                new ClassPathResource("application.yml")
        );

        assertThat(propertySources).anySatisfy(source -> {
            assertThat((String) source.getProperty("spring.datasource.url"))
                    .startsWith("jdbc:mysql://")
                    .doesNotContain("${");
            assertThat((String) source.getProperty("spring.datasource.username")).isNotBlank();
            assertThat((String) source.getProperty("spring.datasource.password")).isNotBlank();
            assertThat(source.getProperty("kunling.robot-bridge.enabled")).isNull();
            assertThat(source.getProperty("kunling.action.compiler.maximum-action-depth")).isNull();
            assertThat(source.getProperty("kunling.action.upstream.enabled")).isNull();
        });

        String yaml = new String(
                java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/application.yml")),
                java.nio.charset.StandardCharsets.UTF_8
        );
        assertThat(yaml).doesNotContain("${");
    }
}
