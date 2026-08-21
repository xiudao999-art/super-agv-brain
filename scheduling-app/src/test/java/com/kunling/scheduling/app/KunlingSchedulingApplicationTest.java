package com.kunling.scheduling.app;

import com.kunling.scheduling.action.config.ActionModuleConfiguration;
import com.kunling.scheduling.agvflow.AgvFlowModuleConfiguration;
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
        assertThat(moduleImport.value()).containsExactly(
                ActionModuleConfiguration.class,
                AgvFlowModuleConfiguration.class
        );
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
    void yamlContainsDeploymentConfigurationForDatabaseTcpListenerAndApiDocs() throws Exception {
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
            assertThat(source.getProperty("kunling.action.robot-bridge.enabled")).isEqualTo(true);
            assertThat(source.getProperty("kunling.action.robot-bridge.bind-address")).isEqualTo("0.0.0.0");
            assertThat(source.getProperty("kunling.action.robot-bridge.port")).isEqualTo(8080);
            assertThat(source.getProperty("springdoc.api-docs.path")).isEqualTo("/v3/api-docs");
            assertThat(source.getProperty("knife4j.enable")).isEqualTo(true);
            assertThat(source.getProperty("spring.web.resources.cache.cachecontrol.no-store"))
                    .isEqualTo(true);
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
