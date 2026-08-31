package com.kunling.scheduling.action.execution.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import com.kunling.scheduling.action.execution.domain.ActionExecutionEventView;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ActionExecutionEventEntityTest {
    @Test
    void persistsStepEventAndPhysicalOutcomeWithoutChangingTheirShape() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        JsonCodec codec = new JsonCodec(mapper);
        RobotActionEvent event = new RobotActionEvent(
                "ACTION_EVENT", "message-1", "session-1", "R01", "action-1", "dc-1", 8L,
                RobotActionEvent.State.FAILED,
                mapper.readTree("{\"eventType\":\"STEP_FAILED\",\"stepId\":\"move\"," +
                        "\"operation\":\"MOVE_TO_MAP_POINT\"}"),
                mapper.readTree("[{\"stepId\":\"move\",\"state\":\"FAILED\"}]"),
                PhysicalOutcome.CONFIRMED_FAILED,
                mapper.readTree("{\"clientCode\":50203,\"message\":\"执行失败\"}"),
                Instant.parse("2026-08-25T02:17:30Z"));

        ActionExecutionEventView view = new ActionExecutionEventEntity(
                event, codec, Instant.parse("2026-08-25T02:17:31Z")).toView(codec);

        assertThat(view.stepEvent().path("eventType").asText()).isEqualTo("STEP_FAILED");
        assertThat(view.stepEvent().path("operation").asText()).isEqualTo("MOVE_TO_MAP_POINT");
        assertThat(view.physicalOutcome()).isEqualTo(PhysicalOutcome.CONFIRMED_FAILED);
    }
}
