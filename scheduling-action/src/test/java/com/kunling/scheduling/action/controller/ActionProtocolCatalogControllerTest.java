package com.kunling.scheduling.action.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.action.definition.domain.MoveToPoseParameters;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionProtocolCatalogControllerTest {

    @Test
    void exposesMoveToPoseJsonTemplateWithDecimalJavaProperties() throws Exception {
        Map<String, Object> catalog = new ActionProtocolCatalogController().catalog();
        Map<?, ?> examples = (Map<?, ?>) catalog.get("parameterExamples");
        MoveToPoseParameters params = (MoveToPoseParameters) examples.get("MOVE_TO_POSE");

        assertThat(params.pose().x()).isInstanceOf(Double.class);
        assertThat(MoveToPoseParameters.ArmPose.class.getDeclaredField("x").getType())
                .isEqualTo(Double.class);
        assertThat(MoveToPoseParameters.class.getDeclaredField("positionToleranceMm").getType())
                .isEqualTo(Double.class);
        assertThat(MoveToPoseParameters.class.getDeclaredField("angleToleranceDeg").getType())
                .isEqualTo(Double.class);

        JsonNode json = new ObjectMapper().valueToTree(params);
        assertThat(json.at("/pose/x").isFloatingPointNumber()).isTrue();
        assertThat(json.path("positionToleranceMm").isFloatingPointNumber()).isTrue();
        assertThat(json.path("angleToleranceDeg").isFloatingPointNumber()).isTrue();
    }
}
