package com.kunling.scheduling.action.robotbridge.compat.cnet8;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kunling.scheduling.action.ActionTestFixtures;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionEntity;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.robotbridge.infrastructure.compat.cnet8.Cnet8ActionEventNormalizer;
import com.kunling.scheduling.action.robotbridge.infrastructure.compat.cnet8.Cnet8ClientCodeMapper;
import com.kunling.scheduling.action.robotbridge.infrastructure.compat.cnet8.Cnet8ExecutionPlanRenderer;
import com.kunling.scheduling.action.robotbridge.infrastructure.compat.cnet8.Cnet8ProtocolAdapter;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class Cnet8ProtocolAdapterTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final Cnet8ClientCodeMapper clientCodeMapper = new Cnet8ClientCodeMapper();
    private final Cnet8ProtocolAdapter adapter = new Cnet8ProtocolAdapter(objectMapper,
            new Cnet8ExecutionPlanRenderer(objectMapper, new JsonCodec(objectMapper)),
            new Cnet8ActionEventNormalizer(objectMapper, clientCodeMapper));

    @Test
    void partiallyCompletedUnknownHoldBecomesFailedAndLetsActionEnterLocalHold() throws Exception {
        RobotActionEvent event = adapter.parseActionEvent(
                event("UNKNOWN_HOLD", "PARTIALLY_COMPLETED", "PHYSICAL_OUTCOME_UNCERTAIN"),
                "R01", "session-1", 7L);

        assertThat(event.state()).isEqualTo(RobotActionEvent.State.FAILED);
        assertThat(event.physicalOutcome()).isEqualTo(PhysicalOutcome.PARTIALLY_COMPLETED);
        assertThat(event.error().path("clientCode").asInt()).isEqualTo(70201);
    }

    @Test
    void unknownPhysicalOutcomeUsesTheStrictActionUnknownState() throws Exception {
        RobotActionEvent event = adapter.parseActionEvent(
                event("UNKNOWN_HOLD", "UNKNOWN", "NEW_CLIENT_CODE"),
                "R01", "session-1", 8L);

        assertThat(event.state()).isEqualTo(RobotActionEvent.State.UNKNOWN);
        assertThat(event.physicalOutcome()).isEqualTo(PhysicalOutcome.UNKNOWN);
        assertThat(event.error().path("clientCode").asInt())
                .isEqualTo(Cnet8ClientCodeMapper.UNMAPPED_CLIENT_CODE);
        assertThat(event.error().path("rawClientCode").asText()).isEqualTo("NEW_CLIENT_CODE");
        assertThat(event.error().path("rawMessageInfo").isObject()).isTrue();
    }

    @Test
    void cnet8AuditFieldsCanEnterTheActionExecutionStateMachine() throws Exception {
        RobotActionEvent event = adapter.parseActionEvent(
                event("UNKNOWN_HOLD", "UNKNOWN", "NEW_CLIENT_CODE"),
                "R01", "session-1", 9L);
        JsonCodec jsonCodec = new JsonCodec(objectMapper);
        ActionExecutionEntity execution = new ActionExecutionEntity(
                ActionTestFixtures.newExecution(), jsonCodec);

        execution.applyEvent(event, jsonCodec, Instant.EPOCH);

        assertThat(execution.toView(jsonCodec).state()).isEqualTo(ActionExecutionState.UNKNOWN_HOLD);
        assertThat(execution.toView(jsonCodec).error().path("rawClientCode").asText())
                .isEqualTo("NEW_CLIENT_CODE");
        assertThat(execution.toView(jsonCodec).error().path("rawMessageInfo").isObject()).isTrue();
    }

    private JsonNode event(String state, String outcome, String clientCode) throws Exception {
        return objectMapper.readTree("{\"MessageId\":\"event-1\"," +
                "\"MessageName\":\"ACTION_EVENT\",\"MessageType\":\"ACTION_EVENT\"," +
                "\"RobotId\":\"R01\",\"ActionInstanceId\":\"action-1\"," +
                "\"DeviceCommandId\":\"dc-1\",\"Timestamp\":\"2026-09-01T01:00:03+00:00\"," +
                "\"MessageInfo\":{\"OriginalMessageId\":\"command-1\"," +
                "\"ActionInstanceId\":\"action-1\",\"DeviceCommandId\":\"dc-1\"," +
                "\"PackageHash\":\"hash\",\"EventKind\":\"FINAL\"," +
                "\"State\":\"" + state + "\",\"PhysicalOutcome\":\"" + outcome + "\"," +
                "\"ClientCode\":\"" + clientCode + "\",\"Message\":\"结果不确定\"," +
                "\"ResolvedSteps\":[{\"StepId\":\"move\",\"Operation\":\"MOVE_TO_MAP_POINT\"," +
                "\"Success\":true,\"Skipped\":false,\"Attempts\":1," +
                "\"PhysicalOutcome\":\"CONFIRMED_SUCCEEDED\",\"Message\":\"已移动\"}]," +
                "\"OccurredAt\":\"2026-09-01T01:00:03+00:00\"}}");
    }
}
