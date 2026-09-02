package com.kunling.scheduling.action.commissioning.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import com.kunling.scheduling.action.execution.application.ActionExecutionStore;
import com.kunling.scheduling.action.robotbridge.application.DispatchReceipt;
import com.kunling.scheduling.action.robotbridge.application.RobotActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotOperationCapability;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArmPositionProbeServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonCodec codec = new JsonCodec(mapper);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);

    @Mock private RobotActionTransport transport;
    @Mock private ActionExecutionStore executionStore;
    private ArmPositionProbeCoordinator coordinator;
    private ArmPositionProbeService service;

    @BeforeEach
    void setUp() {
        coordinator = new ArmPositionProbeCoordinator(8, Duration.ofSeconds(30), clock);
        service = new ArmPositionProbeService(transport, executionStore, coordinator, mapper, codec, clock);
    }

    @Test
    void queryCommandUsesOneReadOnlyRawMoveToPoseStep() {
        RobotActionCommand command = service.createProbeCommand("R01", 8_000);

        assertThat(command.actionInstanceId()).startsWith("arm-position-probe-");
        assertThat(command.deviceCommandId()).matches("[0-9a-f]{32}");
        assertThat(command.packageHash()).matches("[0-9a-f]{64}");
        assertThat(command.input().at("/executionPlan/steps")).hasSize(1);
        assertThat(command.input().at("/executionPlan/steps/0/operation").asText())
                .isEqualTo("MOVE_TO_POSE");
        assertThat(command.input().at("/executionPlan/steps/0/params/commandId").asText())
                .isEqualTo(command.deviceCommandId());
        assertThat(command.input().at("/executionPlan/steps/0/params/armCommandModelType").asInt())
                .isEqualTo(3);
        assertThat(command.input().at("/executionPlan/steps/0/params/armCommandInfo").isNull()).isTrue();
        assertThat(command.input().at("/executionPlan/steps/0/params/armMoveRequestParams/speedPercent").asInt())
                .isEqualTo(10);
        assertThat(command.input().at("/executionPlan/steps/0/params/armMoveRequestParams/armPoseXYZRxRyRz/x")
                .isFloatingPointNumber()).isTrue();
        assertThat(command.input().at("/executionPlan/steps/0/onFailure/default/action").asText())
                .isEqualTo("STOP_AND_REPORT");
    }

    @Test
    void nonRetryProbeDirectiveDoesNotCarryRetryParameters() {
        RobotActionCommand command = service.createProbeCommand("R01", 8_000);
        JsonNode directive = command.input().at("/executionPlan/steps/0/onFailure/default");

        assertThat(directive.has("maxRetries") || directive.has("delayMs"))
                .as("query-arm-position 的非重试策略不能配置重试参数")
                .isFalse();
    }

    @Test
    void successfulTerminalEventExtractsBothPoseRepresentations() {
        when(transport.findSession("R01")).thenReturn(Optional.of(session(true)));
        when(transport.dispatch(any(RobotActionCommand.class))).thenAnswer(invocation -> {
            RobotActionCommand command = invocation.getArgument(0);
            coordinator.route(successEvent(command));
            return new DispatchReceipt("session-1", "message-1", clock.instant());
        });

        ArmPositionProbeResult result = service.probe("R01");

        assertThat(result.robotId()).isEqualTo("R01");
        assertThat(result.capturedAt()).isEqualTo(clock.instant());
        assertThat(result.armMoveRequestType()).isEqualTo(1);
        assertThat(result.speedPercent()).isEqualTo(35);
        assertThat(result.armPoseXYZRxRyRz().x()).isEqualTo(101.5D);
        assertThat(result.armPoseJ1J2J3J4J5J6().j6()).isEqualTo(60.0D);
    }

    @Test
    void activeBusinessActionBlocksProbeBeforeTransportLookup() {
        when(executionStore.hasActiveExecutionForRobot("R01")).thenReturn(true);

        assertThatThrownBy(() -> service.probe("R01"))
                .isInstanceOf(ActionConflictException.class).hasMessageContaining("其他 Action");
        verify(transport, never()).findSession(any());
    }

    @Test
    void offlineAndMissingCapabilityAreReportedAsUnavailable() {
        when(transport.findSession("R01")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.probe("R01"))
                .isInstanceOf(RobotUnavailableException.class).hasMessageContaining("未在线");

        when(transport.findSession("R01")).thenReturn(Optional.of(session(false)));
        assertThatThrownBy(() -> service.probe("R01"))
                .isInstanceOf(RobotUnavailableException.class).hasMessageContaining("MOVE_TO_POSE");
    }

    @Test
    void failedTerminalAndMissingResultDataNeverCreateFakePosition() {
        RobotActionCommand command = service.createProbeCommand("R01", 8_000);
        RobotActionEvent failed = new RobotActionEvent("ACTION_EVENT", "event-failed", "session-1", "R01",
                command.actionInstanceId(), command.deviceCommandId(), 1L, RobotActionEvent.State.FAILED,
                null, null, PhysicalOutcome.CONFIRMED_FAILED,
                mapper.createObjectNode().put("message", "device rejected"), clock.instant());
        assertThatThrownBy(() -> service.extractResult("R01", failed))
                .isInstanceOf(RobotUnavailableException.class).hasMessageContaining("device rejected");

        RobotActionEvent missing = new RobotActionEvent("ACTION_EVENT", "event-missing", "session-1", "R01",
                command.actionInstanceId(), command.deviceCommandId(), 1L, RobotActionEvent.State.FINISHED,
                null, mapper.createArrayNode(), PhysicalOutcome.CONFIRMED_SUCCEEDED, null, clock.instant());
        assertThatThrownBy(() -> service.extractResult("R01", missing))
                .isInstanceOf(RobotUnavailableException.class).hasMessageContaining("resultData");
    }

    private RobotSessionView session(boolean moveToPose) {
        Map<String, RobotOperationCapability> capabilities = new LinkedHashMap<String, RobotOperationCapability>();
        if (moveToPose) capabilities.put("MOVE_TO_POSE",
                new RobotOperationCapability("MOVE_TO_POSE", 1_000, 20_000));
        return new RobotSessionView("session-1", "R01", "ARM", "client-1", capabilities,
                Collections.singleton("STOP_AND_REPORT"), clock.instant(), clock.instant());
    }

    private RobotActionEvent successEvent(RobotActionCommand command) {
        ObjectNode data = mapper.createObjectNode();
        data.put("armMoveRequestType", 1);
        data.put("speedPercent", 35);
        ObjectNode cartesian = data.putObject("armPoseXYZRxRyRz");
        cartesian.put("x", 101.5D).put("y", 202.0D).put("z", 303.0D)
                .put("rx", 1.0D).put("ry", 2.0D).put("rz", 3.0D);
        ObjectNode joints = data.putObject("armPoseJ1J2J3J4J5J6");
        joints.put("j1", 10.0D).put("j2", 20.0D).put("j3", 30.0D)
                .put("j4", 40.0D).put("j5", 50.0D).put("j6", 60.0D);
        ArrayNode resolved = mapper.createArrayNode();
        resolved.addObject().put("stepId", "query-arm-position").put("operation", "MOVE_TO_POSE")
                .put("success", true).set("resultData", data);
        return new RobotActionEvent("ACTION_EVENT", "event-success", "session-1", "R01",
                command.actionInstanceId(), command.deviceCommandId(), 1L, RobotActionEvent.State.FINISHED,
                null, resolved, PhysicalOutcome.CONFIRMED_SUCCEEDED, null, clock.instant());
    }
}
