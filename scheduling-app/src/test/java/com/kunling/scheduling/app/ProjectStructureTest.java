package com.kunling.scheduling.app;

import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectStructureTest {

    @Test
    void robotBridgeBelongsToTheActionModuleInsteadOfAnIndependentMavenModule() throws Exception {
        String rootPom = new String(Files.readAllBytes(Paths.get("../pom.xml")), StandardCharsets.UTF_8);

        assertThat(rootPom).contains("<module>scheduling-common</module>");
        assertThat(rootPom).contains("<module>scheduling-action</module>");
        assertThat(rootPom).contains("<module>scheduling-agvFlow</module>");
        assertThat(rootPom).contains("<module>scheduling-workflow</module>");
        assertThat(rootPom).contains("<module>scheduling-app</module>");
        assertThat(rootPom).doesNotContain("<module>scheduling-robot-bridge</module>");
        assertThat(Paths.get("../scheduling-action/src/main/java/com/kunling/scheduling/action/robotbridge"))
                .exists();
    }

    @Test
    void projectBaselineSupportsJdk8() throws Exception {
        String rootPom = new String(Files.readAllBytes(Paths.get("../pom.xml")), StandardCharsets.UTF_8);

        assertThat(rootPom).contains("<version>2.7.18</version>");
        assertThat(rootPom).contains("<java.version>8</java.version>");
        assertThat(rootPom).doesNotContain("<java.version>21</java.version>");
    }

    @Test
    void actionModuleProvidesKnife4jDocumentationConfiguration() throws Exception {
        String rootPom = new String(Files.readAllBytes(Paths.get("../pom.xml")), StandardCharsets.UTF_8);
        String actionPom = new String(
                Files.readAllBytes(Paths.get("../scheduling-action/pom.xml")),
                StandardCharsets.UTF_8
        );

        assertThat(rootPom).contains("<knife4j.version>4.4.0</knife4j.version>");
        assertThat(actionPom).contains("<artifactId>knife4j-openapi3-spring-boot-starter</artifactId>");
        assertThat(Paths.get(
                "../scheduling-action/src/main/java/com/kunling/scheduling/action/robotbridge/config/Knife4jConfiguration.java"
        )).exists();
    }

    @Test
    void actionControllersUseOneCanonicalPackage() {
        // Controller 迁移后若旧源码目录仍存在，Spring 会按默认 Bean 名注册出同名组件并启动失败。
        assertThat(Paths.get(
                "../scheduling-action/src/main/java/com/kunling/scheduling/action/controller"
        )).isDirectory();
        assertThat(Paths.get(
                "../scheduling-action/src/main/java/com/kunling/scheduling/action/interfaces/controller"
        )).doesNotExist();
        assertThat(Paths.get(
                "../scheduling-action/src/main/java/com/kunling/scheduling/action/interfaces/rest"
        )).doesNotExist();
    }

    @Test
    void managementControllersAndDomainLayersBelongToApplication() {
        assertThat(Paths.get(
                "../scheduling-agvFlow/src/main/java/com/kunling/scheduling/agvflow/labconfig"
        )).doesNotExist();
        for (String controller : new String[]{
                "LocationController", "LocationTypeController", "CarrierController",
                "CarrierTypeController", "LabController", "LabConfigController"
        }) {
            assertThat(Paths.get("src/main/java/com/kunling/scheduling/app/controller/"
                    + controller + ".java")).isRegularFile();
            assertThat(Paths.get("../scheduling-agvFlow/src/main/java/com/kunling/scheduling/agvflow/controller/"
                    + controller + ".java")).doesNotExist();
        }
        assertThat(Paths.get(
                "src/main/java/com/kunling/scheduling/app/service/LabConfigApplicationService.java"
        )).isRegularFile();
        assertThat(Paths.get(
                "src/main/java/com/kunling/scheduling/app/mapper/LabConfigMapper.java"
        )).isRegularFile();
    }

    @Test
    void commonModuleOwnsTheOnlyGlobalExceptionHandler() {
        assertThat(Paths.get(
                "../scheduling-common/src/main/java/com/kunling/scheduling/common/web/GlobalExceptionHandler.java"
        )).isRegularFile();
        assertThat(Paths.get(
                "../scheduling-action/src/main/java/com/kunling/scheduling/action/controller/ApiExceptionHandler.java"
        )).doesNotExist();
        assertThat(Paths.get(
                "../scheduling-agvFlow/src/main/java/com/kunling/scheduling/agvflow/config/GlobalExceptionHandler.java"
        )).doesNotExist();
        assertThat(Paths.get(
                "../scheduling-workflow/src/main/java/com/kunling/scheduling/workflow/controller/WorkflowExceptionHandler.java"
        )).doesNotExist();
    }

    @Test
    void allEnabledControllerEndpointsReturnApiResultDirectly() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> violations = new ArrayList<>();
        for (String basePackage : new String[]{
                "com.kunling.scheduling.app.controller",
                "com.kunling.scheduling.action.controller",
                "com.kunling.scheduling.app.controller",
                "com.kunling.scheduling.workflow.controller"
        }) {
            scanner.findCandidateComponents(basePackage).forEach(candidate -> {
                Class<?> controllerType = loadClass(candidate.getBeanClassName());
                if (!BaseController.class.isAssignableFrom(controllerType)) {
                    violations.add(controllerType.getName() + " 未继承 BaseController");
                }
                for (Method method : controllerType.getDeclaredMethods()) {
                    if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) != null
                            && !ApiResult.class.equals(method.getReturnType())) {
                        violations.add(controllerType.getSimpleName() + "#" + method.getName()
                                + " 返回 " + method.getGenericReturnType().getTypeName());
                    }
                }
            });
        }

        assertThat(violations)
                .as("所有启用中的 Controller 接口必须直接返回 ApiResult<T>")
                .isEmpty();
    }

    @Test
    void databaseUsesImmutableCreateBaselineAndAppendOnlyAlterScripts() throws Exception {
        String appPom = new String(Files.readAllBytes(Paths.get("pom.xml")), StandardCharsets.UTF_8);
        String applicationYaml = new String(
                Files.readAllBytes(Paths.get("src/main/resources/application.yml")),
                StandardCharsets.UTF_8
        );
        java.nio.file.Path createScript = Paths.get(
                "../scheduling-action/src/main/resources/db/create/kunling_action_schema.sql"
        );
        java.nio.file.Path dynamicActionAlterScript = Paths.get(
                "../scheduling-action/src/main/resources/db/alter/20260820_01_dynamic_action_package.sql"
        );
        java.nio.file.Path removeBusinessInputAlterScript = Paths.get(
                "../scheduling-action/src/main/resources/db/alter/20260821_01_remove_action_business_input.sql"
        );

        assertThat(appPom.toLowerCase()).doesNotContain("flyway");
        assertThat(applicationYaml.toLowerCase()).doesNotContain("flyway");
        assertThat(createScript).isRegularFile();
        assertThat(dynamicActionAlterScript).isRegularFile();
        assertThat(removeBusinessInputAlterScript).isRegularFile();
        assertThat(Paths.get("../scheduling-action/src/main/resources/db/alter")).isDirectory();
        assertThat(Paths.get(
                "../scheduling-action/src/main/java/com/kunling/scheduling/action/definition/application/StandardActionSeed.java"
        )).doesNotExist();

        String schema = new String(Files.readAllBytes(createScript), StandardCharsets.UTF_8);
        String dynamicActionAlter = new String(
                Files.readAllBytes(dynamicActionAlterScript),
                StandardCharsets.UTF_8
        ).toLowerCase();
        String removeBusinessInputAlter = new String(
                Files.readAllBytes(removeBusinessInputAlterScript),
                StandardCharsets.UTF_8
        ).toLowerCase();

        // CREATE 文件是已投产的不可变基线；统一换行后校验摘要，防止后续误改表结构或种子数据。
        assertThat(normalizedSha256(schema))
                .isEqualTo("42349349f94e79b77f3f75b206629708279be2c19d1b045ae1b9f7d77518f302");

        // 新 Action 表只能由追加式 ALTER 创建，不能回写到不可变 CREATE 基线。
        assertThat(dynamicActionAlter)
                .contains(
                        "drop table action_execution_node;",
                        "drop table action_execution;",
                        "drop table robot_action_event;",
                        "drop table robot_action_execution;",
                        "drop table action_draft;",
                        "drop table action_release;",
                        "drop table atomic_capability;"
                )
                .doesNotContain("rename table", "legacy_")
                .contains("create table action_definition")
                .contains("create table action_parameter_set")
                .contains("create table action_execution")
                .contains("create table action_execution_event")
                .contains("insert into action_definition")
                .doesNotContain("\n    action_version varchar")
                .contains(
                        "'arm.home','arm.home',1,'draft'",
                        "'arm.pick','arm.pick',1,'draft'",
                        "'arm.pick_batch','arm.pick_batch',1,'draft'",
                        "'arm.place','arm.place',1,'draft'",
                        "'arm.place_batch','arm.place_batch',1,'draft'",
                        "'move','move',1,'draft'",
                        "'vision.capture','vision.capture',1,'draft'"
                );

        // 明细表引用主表，删除顺序必须先明细后主表。
        assertThat(dynamicActionAlter.indexOf("drop table action_execution_node;"))
                .isLessThan(dynamicActionAlter.indexOf("drop table action_execution;"));
        assertThat(dynamicActionAlter.indexOf("drop table robot_action_event;"))
                .isLessThan(dynamicActionAlter.indexOf("drop table robot_action_execution;"));

        // 业务入参迁移必须保留旧配置含义，再通过追加式 DDL 清理冗余快照列。
        assertThat(removeBusinessInputAlter)
                .contains("json_merge_patch")
                .contains("json_remove(cast(definition_json as json), '$.inputschema')")
                .contains("'$input.'")
                .contains("'$parameters.'")
                .contains("drop column input_snapshot_json");
    }

    private String normalizedSha256(String content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(content.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
        StringBuilder hexadecimal = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hexadecimal.append(String.format("%02x", value & 0xff));
        }
        return hexadecimal.toString();
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("无法加载 Controller: " + className, exception);
        }
    }
}
