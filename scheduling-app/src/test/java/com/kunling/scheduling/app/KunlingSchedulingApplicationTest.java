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
        assertThat(moduleImport.value()).contains(ActionModuleConfiguration.class);
    }

    @Test
    void applicationUsesSchedulingServiceName() throws Exception {
        var propertySources = new YamlPropertySourceLoader().load(
                "application",
                new ClassPathResource("application.yml")
        );

        assertThat(propertySources)
                .anySatisfy(source -> assertThat(source.getProperty("spring.application.name"))
                        .isEqualTo("kunling-scheduling"));
    }
}
