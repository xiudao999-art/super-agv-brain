package com.kunling.scheduling.action.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionProtocolCatalogControllerTest {
    private final ActionProtocolCatalogController controller = new ActionProtocolCatalogController(
            new ActionProtocolParameterExamples(new ObjectMapper()));

    @Test
    void exposesRawMoveToPoseWireParametersInsteadOfLegacyHighLevelFields() {
        Map<?, ?> examples = (Map<?, ?>) controller.catalog().get("parameterExamples");
        JsonNode params = (JsonNode) examples.get("MOVE_TO_POSE");

        assertThat(params.path("commandId").asText()).matches("[0-9a-f]{32}");
        assertThat(params.path("armCommandModelType").isIntegralNumber()).isTrue();
        assertThat(params.path("armCommandInfo").isNull()).isTrue();
        assertThat(params.at("/armMoveRequestParams/armMoveRequestType").isIntegralNumber()).isTrue();
        assertThat(params.at("/armMoveRequestParams/speedPercent").isIntegralNumber()).isTrue();
        assertThat(params.at("/armMoveRequestParams/speedPercent").asInt()).isEqualTo(10);
        assertThat(params.at("/armMoveRequestParams/armPoseXYZRxRyRz/x").asDouble()).isEqualTo(300.0D);
        assertThat(params.at("/armMoveRequestParams/armPoseXYZRxRyRz/z").asDouble()).isEqualTo(400.0D);
        assertThat(params.at("/armMoveRequestParams/armPoseXYZRxRyRz/rx").asDouble()).isEqualTo(180.0D);
        assertThat(params.at("/armMoveRequestParams/armPoseXYZRxRyRz/x").isFloatingPointNumber()).isTrue();
        assertThat(params.at("/armMoveRequestParams/armPoseJ1J2J3J4J5J6/j6").isFloatingPointNumber()).isTrue();
        assertThat(params.has("station")).isFalse();
        assertThat(params.has("poseRole")).isFalse();
    }

    @Test
    void exposesGripperExamplesForDotAndUnderscoreAliases() {
        Map<?, ?> examples = (Map<?, ?>) controller.catalog().get("parameterExamples");
        JsonNode dotted = (JsonNode) examples.get("GRIP.OPEN");
        JsonNode underscored = (JsonNode) examples.get("GRIP_OPEN");

        assertThat(dotted).isEqualTo(underscored);
        assertThat(dotted.path("gripperCommandModelType").isIntegralNumber()).isTrue();
        assertThat(dotted.path("gripperCommandInfo").isNull()).isTrue();
        assertThat(dotted.at("/gripperMoveRequestParams/targetWidthPercent").asInt()).isEqualTo(100);
        assertThat(dotted.at("/gripperMoveRequestParams/forcePercent").asInt()).isEqualTo(40);
        assertThat(dotted.at("/gripperMoveRequestParams/speedPercent").asInt()).isEqualTo(20);
        JsonNode verifyLoad = (JsonNode) examples.get("GRIP_VERIFY_LOAD");
        assertThat(verifyLoad).isEqualTo(examples.get("GRIP.VERIFY_LOAD"));
        assertThat(verifyLoad.path("gripperCommandModelType").asInt()).isEqualTo(3);
        assertThat(verifyLoad.at("/gripperMoveRequestParams/forcePercent").asInt()).isEqualTo(30);
    }
}
