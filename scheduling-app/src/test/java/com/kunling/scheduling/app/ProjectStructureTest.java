package com.kunling.scheduling.app;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectStructureTest {

    @Test
    void robotBridgeBelongsToTheActionModuleInsteadOfAnIndependentMavenModule() throws Exception {
        String rootPom = new String(Files.readAllBytes(Paths.get("../pom.xml")), StandardCharsets.UTF_8);

        assertThat(rootPom).contains("<module>scheduling-action</module>");
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
                "../scheduling-action/src/main/java/com/kunling/scheduling/action/interfaces/docs/Knife4jConfiguration.java"
        )).exists();
    }

    @Test
    void databaseIsMaintainedByOneManualCreateScriptAndFutureAlterScripts() throws Exception {
        String appPom = new String(Files.readAllBytes(Paths.get("pom.xml")), StandardCharsets.UTF_8);
        String applicationYaml = new String(
                Files.readAllBytes(Paths.get("src/main/resources/application.yml")),
                StandardCharsets.UTF_8
        );
        java.nio.file.Path createScript = Paths.get(
                "../scheduling-action/src/main/resources/db/create/kunling_action_schema.sql"
        );

        assertThat(appPom.toLowerCase()).doesNotContain("flyway");
        assertThat(applicationYaml.toLowerCase()).doesNotContain("flyway");
        assertThat(createScript).isRegularFile();
        assertThat(Paths.get("../scheduling-action/src/main/resources/db/alter")).isDirectory();
        assertThat(Paths.get(
                "../scheduling-action/src/main/java/com/kunling/scheduling/action/definition/application/StandardActionSeed.java"
        )).doesNotExist();

        String schema = new String(Files.readAllBytes(createScript), StandardCharsets.UTF_8).toLowerCase();
        assertThat(schema)
                .contains("create table action_draft")
                .contains("create table action_release")
                .contains("create table atomic_capability")
                .contains("create table action_execution")
                .contains("create table action_execution_node")
                .contains("create table robot_action_execution")
                .contains("create table robot_action_event")
                .contains("insert into action_draft")
                .contains(
                        "'arm.home', '1.0.0', 1, 'draft'",
                        "'arm.pick', '1.0.0', 1, 'draft'",
                        "'arm.pick_batch', '1.0.0', 1, 'draft'",
                        "'arm.place', '1.0.0', 1, 'draft'",
                        "'arm.place_batch', '1.0.0', 1, 'draft'",
                        "'move', '1.0.0', 1, 'draft'",
                        "'vision.capture', '1.0.0', 1, 'draft'"
                );
    }
}
