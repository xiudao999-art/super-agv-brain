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
}
