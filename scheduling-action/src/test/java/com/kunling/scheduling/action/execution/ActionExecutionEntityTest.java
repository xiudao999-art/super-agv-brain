package com.kunling.scheduling.action.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.ActionTestFixtures;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionEntity;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionExecutionEntityTest {
    private final JsonCodec codec = new JsonCodec(ActionTestFixtures.MAPPER);

    @Test
    void newExecutionStartsPendingAndKeepsCommandEvidence() {
        ActionExecutionEntity entity = entity();
        assertThat(entity.toView(codec).state()).isEqualTo(ActionExecutionState.DISPATCH_PENDING);
        assertThat(entity.toView(codec).actionDefinitionId()).isEqualTo("definition-1");
        assertThat(entity.toView(codec).commandInput()).isEqualTo(ActionTestFixtures.executionPlan());
    }

    @Test
    void acceptedRunningAndFinishedFollowForwardStateTransitions() {
        ActionExecutionEntity entity = entity();
        entity.applyEvent(event(1, RobotActionEvent.State.ACCEPTED, null, null), codec, Instant.EPOCH);
        entity.applyEvent(event(2, RobotActionEvent.State.RUNNING, null, null), codec, Instant.EPOCH);
        entity.applyEvent(event(3, RobotActionEvent.State.FINISHED,
                PhysicalOutcome.CONFIRMED_SUCCEEDED, null), codec, Instant.EPOCH);
        assertThat(entity.toView(codec).state()).isEqualTo(ActionExecutionState.FINISHED);
    }

    @Test
    void rejectedRequiresNotStartedAndError() {
        ActionExecutionEntity entity = entity();
        assertThatThrownBy(() -> entity.applyEvent(event(1, RobotActionEvent.State.REJECTED,
                PhysicalOutcome.CONFIRMED_FAILED, error()), codec, Instant.EPOCH))
                .hasMessageContaining("NOT_STARTED");
    }

    @Test
    void failedUnknownEntersUnknownHold() {
        ActionExecutionEntity entity = entity();
        entity.applyEvent(event(1, RobotActionEvent.State.FAILED,
                PhysicalOutcome.UNKNOWN, error()), codec, Instant.EPOCH);
        assertThat(entity.toView(codec).state()).isEqualTo(ActionExecutionState.UNKNOWN_HOLD);
    }

    @Test
    void terminalExecutionOnlyAcceptsLaterEventsAsEvidence() {
        ActionExecutionEntity entity = entity();
        entity.applyEvent(event(1, RobotActionEvent.State.FINISHED,
                PhysicalOutcome.CONFIRMED_SUCCEEDED, null), codec, Instant.EPOCH);
        entity.applyEvent(event(2, RobotActionEvent.State.RUNNING, null, null), codec, Instant.EPOCH);
        assertThat(entity.toView(codec).state()).isEqualTo(ActionExecutionState.FINISHED);
        assertThat(entity.toView(codec).lastEventSequence()).isEqualTo(2L);
    }

    @Test
    void eventIdentityMustMatchExecution() {
        ActionExecutionEntity entity = entity();
        RobotActionEvent wrong = new RobotActionEvent("ACTION_EVENT", "event-1", "session-1",
                "R02", "action-1", "dc-1", 1, RobotActionEvent.State.RUNNING,
                null, null, null, null, Instant.EPOCH);
        assertThatThrownBy(() -> entity.applyEvent(wrong, codec, Instant.EPOCH))
                .hasMessageContaining("身份");
    }

    @Test
    void rejectsBusinessFieldsInDownstreamError() {
        ActionExecutionEntity entity = entity();
        JsonNode invalid = error().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid).put("businessCode", "3001");
        assertThatThrownBy(() -> entity.applyEvent(event(1, RobotActionEvent.State.REJECTED,
                PhysicalOutcome.NOT_STARTED, invalid), codec, Instant.EPOCH))
                .hasMessageContaining("不允许携带业务字段");
    }

    private ActionExecutionEntity entity() {
        return new ActionExecutionEntity(ActionTestFixtures.newExecution(), codec);
    }

    private RobotActionEvent event(long sequence, RobotActionEvent.State state,
                                   PhysicalOutcome outcome, JsonNode error) {
        return new RobotActionEvent("ACTION_EVENT", "event-" + sequence, "session-1", "R01",
                "action-1", "dc-1", sequence, state, null,
                state == RobotActionEvent.State.FINISHED ? ActionTestFixtures.MAPPER.createArrayNode() : null,
                outcome, error, Instant.EPOCH);
    }

    private JsonNode error() {
        return ActionTestFixtures.MAPPER.createObjectNode()
                .put("clientCode", 40202).put("message", "参数错误");
    }
}
