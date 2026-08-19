package com.kunling.scheduling.action.fixed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kunling.scheduling.action.fixed.application.ClasspathFixedActionPackageCatalog;
import com.kunling.scheduling.action.fixed.domain.FixedActionType;
import com.kunling.scheduling.action.fixed.domain.MaterializedFixedActionPackage;
import com.kunling.scheduling.action.shared.JsonCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedActionPackageCatalogTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final ClasspathFixedActionPackageCatalog catalog =
            new ClasspathFixedActionPackageCatalog(objectMapper, new JsonCodec(objectMapper));

    @Test
    void moveInputIsMaterializedIntoOneCompleteUpstreamPackage() throws Exception {
        JsonNode input = objectMapper.readTree("{\n  \"pointName\":\"P01\",\n  \"speed\":0.5,\n  \"pose\":{\"x\":12480,\"y\":8220,\"yaw\":90,\"map\":\"LAB\"},\n  \"arrival\":{\"positionToleranceMm\":5,\"angleToleranceDeg\":5,\"timeoutMs\":30000}\n}\n");

        MaterializedFixedActionPackage result = catalog.materialize(FixedActionType.MOVE, input);
        JsonNode mainAction = result.commandInput().path("MainAction");

        assertThat(result.actionVersion()).isEqualTo("1.0");
        assertThat(result.templateVersion()).isEqualTo("1.0.0");
        assertThat(result.timeoutMs()).isEqualTo(35_000);
        assertThat(result.packageHash()).hasSize(64);
        assertThat(mainAction.path("actionType").textValue()).isEqualTo("MOVE");
        assertThat(mainAction.path("phases")).hasSize(1);
        assertThat(mainAction.at("/phases/0/subAction").textValue()).isEqualTo("MOVE_TO_MAP_POINT");
        assertThat(mainAction.at("/phases/0/params/pose/x").doubleValue()).isEqualTo(12480);
        assertThat(result.commandInput().toString()).doesNotContain("$input");
    }

    @Test
    void pickAddsBusinessContextWithoutAllowingCallersToReplacePhases() throws Exception {
        MaterializedFixedActionPackage result = catalog.materialize(
                FixedActionType.ARM_PICK,
                objectMapper.readTree("{\"station\":\"PICK_01\",\"point\":\"SLOT_A\",\"graspProfile\":\"DEFAULT_PICK\",\n \"expectedMaterial\":\"BOX\"}\n")
        );

        JsonNode phases = result.commandInput().at("/MainAction/phases");
        assertThat(phases).hasSize(8);
        assertThat(phases.get(0).at("/params/station").textValue()).isEqualTo("PICK_01");
        assertThat(phases.get(0).at("/params/point").textValue()).isEqualTo("SLOT_A");
        assertThat(phases.get(2).at("/params/expectedMaterial").textValue()).isEqualTo("BOX");
        assertThat(phases.get(5).at("/params/graspProfile").textValue()).isEqualTo("DEFAULT_PICK");
    }

    @Test
    void missingMoveCoordinateOrUnknownInputIsRejectedBeforeDispatch() throws Exception {
        assertThatThrownBy(() -> catalog.materialize(FixedActionType.MOVE,
                objectMapper.readTree("{\"pointName\":\"P01\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pose");

        assertThatThrownBy(() -> catalog.materialize(FixedActionType.ARM_HOME,
                objectMapper.readTree("{\"phases\":[]}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的输入参数");
    }

    @Test
    void packageHashDoesNotDependOnRequestPropertyOrder() throws Exception {
        JsonNode first = objectMapper.readTree("{\"pointName\":\"P01\",\"speed\":0.5,\n \"pose\":{\"x\":1,\"y\":2,\"yaw\":3,\"map\":\"LAB\"}}\n");
        JsonNode second = objectMapper.readTree("{\"pose\":{\"map\":\"LAB\",\"yaw\":3,\"y\":2,\"x\":1},\n \"speed\":0.5,\"pointName\":\"P01\"}\n");

        assertThat(catalog.materialize(FixedActionType.MOVE, first).packageHash())
                .isEqualTo(catalog.materialize(FixedActionType.MOVE, second).packageHash());
    }
}
