package com.kunling.scheduling.action.fixed.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kunling.scheduling.action.fixed.domain.FixedActionType;
import com.kunling.scheduling.action.fixed.domain.NewRobotActionExecution;
import com.kunling.scheduling.action.fixed.domain.RobotActionExecutionState;
import com.kunling.scheduling.action.shared.JsonCodec;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RobotActionExecutionEntityTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final JsonCodec jsonCodec = new JsonCodec(objectMapper);
    private final Instant createdAt = Instant.parse("2026-08-19T01:00:00Z");

    @Test
    void failedEventWithoutKnownPhysicalResultBecomesUnknownHold() throws Exception {
        RobotActionExecutionEntity execution = execution();
        execution.markDispatched("session-1", "command-1", createdAt.plusSeconds(1));
        execution.applyEvent(event(RobotActionEvent.State.FAILED,
                objectMapper.readTree("{\"physicalResultKnown\":false,\"message\":\"timeout\"}"), 3),
                jsonCodec, createdAt.plusSeconds(3));

        assertThat(execution.getState()).isEqualTo(RobotActionExecutionState.UNKNOWN_HOLD);
        assertThat(execution.isPhysicalResultKnown()).isFalse();
        assertThat(execution.getCompletedAt()).isEqualTo(createdAt.plusSeconds(3));
    }

    @Test
    void runningCannotRegressAndHoldCannotAutomaticallyBecomeDone() throws Exception {
        RobotActionExecutionEntity execution = execution();
        execution.markDispatched("session-1", "command-1", createdAt.plusSeconds(1));
        execution.applyEvent(event(RobotActionEvent.State.RUNNING, null, 2), jsonCodec,
                createdAt.plusSeconds(2));
        execution.applyEvent(event(RobotActionEvent.State.ACCEPTED, null, 1), jsonCodec,
                createdAt.plusSeconds(3));
        assertThat(execution.getState()).isEqualTo(RobotActionExecutionState.RUNNING);

        execution.hold("CONNECTION_LOST", "连接中断", jsonCodec, createdAt.plusSeconds(4));
        execution.applyEvent(event(RobotActionEvent.State.PHYSICAL_DONE, null, 4), jsonCodec,
                createdAt.plusSeconds(5));
        assertThat(execution.getState()).isEqualTo(RobotActionExecutionState.UNKNOWN_HOLD);
    }

    @Test
    void executionTimeoutIsMeasuredFromThePersistedCreationTime() throws Exception {
        RobotActionExecutionEntity execution = execution();

        assertThat(execution.isTimedOutAt(createdAt.plusSeconds(34))).isFalse();
        assertThat(execution.isTimedOutAt(createdAt.plusSeconds(35))).isTrue();
    }

    private RobotActionExecutionEntity execution() throws Exception {
        NewRobotActionExecution created = new NewRobotActionExecution("action-1", "ROBOT-01", "device-1",
                FixedActionType.MOVE, "1.0", "1.0.0", "request-hash", "package-hash",
                null, null, objectMapper.readTree("{}"),
                objectMapper.readTree("{\"MainAction\":{\"actionType\":\"MOVE\"}}"), 35_000, createdAt);
        return new RobotActionExecutionEntity(created, jsonCodec);
    }

    private RobotActionEvent event(RobotActionEvent.State state, com.fasterxml.jackson.databind.JsonNode error,
                                   long sequence) {
        return new RobotActionEvent("ACTION_EVENT", "event-" + sequence, "session-1", "ROBOT-01",
                "action-1", "device-1", sequence, state, null, null, error,
                createdAt.plusSeconds(sequence));
    }
}
