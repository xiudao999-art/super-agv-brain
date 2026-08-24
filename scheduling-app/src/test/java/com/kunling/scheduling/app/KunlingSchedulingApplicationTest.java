package com.kunling.scheduling.app;

import com.kunling.scheduling.action.config.ActionModuleConfiguration;
import com.kunling.scheduling.agvflow.AgvFlowModuleConfiguration;
import com.kunling.scheduling.workflow.WorkflowModuleConfiguration;
import com.kunling.scheduling.app.config.SchedulingAppOpenApiConfiguration;
import com.kunling.scheduling.app.controller.ImageUploadController;
import com.kunling.scheduling.app.file.ImageStorageService;
import com.kunling.scheduling.app.file.ImageUploadResult;
import com.kunling.scheduling.common.exception.InvalidRequestException;
import com.kunling.scheduling.common.web.ApiResult;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.mock.web.MockMultipartFile;
import org.springdoc.core.GroupedOpenApi;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;

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
                AgvFlowModuleConfiguration.class,
                WorkflowModuleConfiguration.class
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
    void openApiGroupsMatchTheirModuleBoundaries() {
        GroupedOpenApi group = new AgvFlowModuleConfiguration().agvFlowApiGroup();
        GroupedOpenApi resourceConfigGroup =
                new SchedulingAppOpenApiConfiguration().resourceConfigApiGroup();

        assertThat(group.getPackagesToScan())
                .containsExactly("com.kunling.scheduling.agvflow");
        assertThat(group.getPathsToMatch())
                .containsExactly("/api/flow-templates/**", "/nodeRules/**");
        assertThat(resourceConfigGroup.getPackagesToScan())
                .containsExactly("com.kunling.scheduling.app.controller");
        assertThat(resourceConfigGroup.getPathsToMatch()).containsExactly(
                "/api/lab-spaces/**", "/api/lab-configs/**",
                "/api/files/**", "/locations/**", "/locationTypes/**",
                "/carriers/**", "/carrierTypes/**");
    }

    @Test
    void imageUploadStoresVerifiedImageAndReturnsMapImageUrl(@TempDir Path directory) throws Exception {
        ImageStorageService storageService = new ImageStorageService(directory.toString());
        ImageUploadController controller = new ImageUploadController(storageService);
        byte[] png = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
        };

        ApiResult<ImageUploadResult> result = controller.upload(
                new MockMultipartFile("file", "map.png", "image/png", png));

        assertThat(result.getCode()).isEqualTo(200);
        String imageUrl = result.getData().getImageUrl();
        assertThat(imageUrl).startsWith("/files/").endsWith(".png");
        assertThat(Files.exists(directory.resolve(imageUrl.substring("/files/".length())))).isTrue();
    }

    @Test
    void imageUploadRejectsContentThatIsNotAnImage(@TempDir Path directory) {
        ImageStorageService storageService = new ImageStorageService(directory.toString());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storageService.store(
                        new MockMultipartFile("file", "map.txt", "text/plain", "not-image".getBytes())))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("仅支持 PNG、JPEG、GIF、WEBP 图片");
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
            assertThat(source.getProperty("kunling.file.storage-directory"))
                    .isEqualTo("scheduling-app/src/main/resources/file");
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
