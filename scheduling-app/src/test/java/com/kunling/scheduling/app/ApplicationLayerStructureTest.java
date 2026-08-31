package com.kunling.scheduling.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 防止 scheduling-app 再次退回按单一功能堆叠顶层包的目录结构。 */
class ApplicationLayerStructureTest {

    private static final Path APP_PACKAGE = Paths.get(
            "src/main/java/com/kunling/scheduling/app");

    @Test
    void app模块业务代码遵循传统Spring分层() throws Exception {
        assertJavaFile("controller/OperationLogController.java");
        assertJavaFile("controller/HomeTestController.java");
        assertJavaFile("controller/ImageUploadController.java");
        assertJavaFile("controller/ActionParameterSchemaController.java");

        assertJavaFile("domain/SystemOperationLog.java");
        assertJavaFile("domain/HomeOverviewResponse.java");
        assertJavaFile("domain/ImageUploadResult.java");
        assertJavaFile("domain/ActionParameterSchema.java");

        assertJavaFile("service/OperationLogService.java");
        assertJavaFile("service/HomeOverviewTestService.java");
        assertJavaFile("service/ImageStorageService.java");
        assertJavaFile("service/ActionParameterSchemaService.java");

        assertJavaFile("mapper/ActionParameterSchemaMapper.java");

        assertJavaFile("mapper/OperationLogMapper.java");
        assertJavaFile("mapper/HomeTestDataMapper.java");
        assertJavaFile("aspect/OperationLogAspect.java");
        assertJavaFile("config/FileWebConfiguration.java");

        assertThat(javaFilesUnder("systemlog")).isEmpty();
        assertThat(javaFilesUnder("file")).isEmpty();
        assertThat(javaFilesUnder("hometest")).isEmpty();
    }

    private void assertJavaFile(String relativePath) {
        assertThat(APP_PACKAGE.resolve(relativePath)).isRegularFile();
    }

    private List<Path> javaFilesUnder(String relativePath) throws IOException {
        Path directory = APP_PACKAGE.resolve(relativePath);
        if (!Files.exists(directory)) {
            return Collections.emptyList();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }
}
