package com.kunling.scheduling.action.commissioning.application;

import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArmPositionProbeCoordinatorTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void sameRobotAllowsOnlyOneActiveProbeAndTerminalEventCompletesIt() {
        ArmPositionProbeCoordinator coordinator = new ArmPositionProbeCoordinator(4,
                Duration.ofSeconds(30), clock);
        ArmPositionProbeCoordinator.ProbeTicket ticket = coordinator.register("R01", "probe-1", "device-1");

        assertThatThrownBy(() -> coordinator.register("R01", "probe-2", "device-2"))
                .isInstanceOf(ActionConflictException.class);
        assertThat(coordinator.route(event("R01", "probe-1", RobotActionEvent.State.RUNNING))).isTrue();
        assertThat(coordinator.activeProbeCount()).isEqualTo(1);
        coordinator.route(event("R01", "probe-1", RobotActionEvent.State.FINISHED));

        assertThat(ticket.await(Duration.ofMillis(10)).state()).isEqualTo(RobotActionEvent.State.FINISHED);
        assertThat(coordinator.activeProbeCount()).isZero();
    }

    @Test
    void registryIsBoundedAndRetiredProbeStillConsumesLateEvents() {
        ArmPositionProbeCoordinator coordinator = new ArmPositionProbeCoordinator(1,
                Duration.ofSeconds(30), clock);
        ArmPositionProbeCoordinator.ProbeTicket ticket = coordinator.register("R01", "probe-1", "device-1");

        assertThatThrownBy(() -> coordinator.register("R02", "probe-2", "device-2"))
                .isInstanceOf(RobotUnavailableException.class).hasMessageContaining("已满");
        ticket.close();
        assertThat(coordinator.route(event("R01", "probe-1", RobotActionEvent.State.FINISHED))).isTrue();
        assertThat(coordinator.route(event("R02", "business-action", RobotActionEvent.State.FINISHED))).isFalse();
    }

    @Test
    void timeoutCleansActiveRegistrationAndIsolatesLateTerminalEvent() {
        ArmPositionProbeCoordinator coordinator = new ArmPositionProbeCoordinator(2,
                Duration.ofSeconds(30), clock);
        ArmPositionProbeCoordinator.ProbeTicket ticket = coordinator.register(
                "R01", "probe-timeout", "device-1");

        assertThatThrownBy(() -> ticket.await(Duration.ofMillis(1)))
                .isInstanceOf(RobotUnavailableException.class).hasMessageContaining("超时");
        assertThat(coordinator.activeProbeCount()).isZero();
        assertThat(coordinator.route(event("R01", "probe-timeout", RobotActionEvent.State.FINISHED))).isTrue();
    }

    @Test
    void disconnectFailsOnlyThePendingProbeForThatRobot() {
        ArmPositionProbeCoordinator coordinator = new ArmPositionProbeCoordinator(2,
                Duration.ofSeconds(30), clock);
        ArmPositionProbeCoordinator.ProbeTicket ticket = coordinator.register("R01", "probe-1", "device-1");

        coordinator.failRobot("R01", "连接中断");

        assertThatThrownBy(() -> ticket.await(Duration.ofMillis(10)))
                .isInstanceOf(RobotUnavailableException.class).hasMessageContaining("连接中断");
    }

    private RobotActionEvent event(String robotId, String actionInstanceId, RobotActionEvent.State state) {
        return new RobotActionEvent("ACTION_EVENT", "event-" + state, "session-1", robotId,
                actionInstanceId, "device-1", 1L, state, null, null, null, null, clock.instant());
    }
}
