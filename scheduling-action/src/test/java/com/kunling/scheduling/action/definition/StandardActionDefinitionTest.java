package com.kunling.scheduling.action.definition;

import com.kunling.scheduling.action.shared.ImmutableCollections;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StandardActionDefinitionTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    void allTianjinStandardActionsFollowTheDefinitionContract() throws Exception {
        Path directory = java.nio.file.Paths.get("src/main/resources/standard-actions");
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            java.util.List<ActionDefinition> definitions = files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readDefinition)
                    .collect(ImmutableCollections.toImmutableList());

            assertThat(definitions).hasSize(7);
            assertThat(definitions)
                    .allSatisfy(definition -> {
                        assertThat(definition.actionKey()).isNotBlank();
                        assertThat(definition.version()).matches("\\d+\\.\\d+\\.\\d+");
                        assertThat(definition.scope()).isEqualTo("TIANJIN");
                        assertThat(definition.steps()).isNotEmpty();
                    });
        }
    }

    private ActionDefinition readDefinition(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), ActionDefinition.class);
        } catch (Exception exception) {
            throw new AssertionError("标准 Action 无法解析：" + path.getFileName(), exception);
        }
    }
}
