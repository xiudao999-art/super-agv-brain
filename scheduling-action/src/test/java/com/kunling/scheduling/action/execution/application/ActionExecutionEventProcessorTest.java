package com.kunling.scheduling.action.execution.application;

import com.kunling.scheduling.action.commissioning.application.ArmPositionProbeCoordinator;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionExecutionEventProcessorTest {
    @Mock private ActionExecutionStore executionStore;
    @Mock private ActionExecutionReportPublisher reportPublisher;
    @Mock private ArmPositionProbeCoordinator probeCoordinator;
    private ActionExecutionEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ActionExecutionEventProcessor(executionStore,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), reportPublisher, probeCoordinator);
    }

    @Test
    void probeEventIsInterceptedBeforeBusinessExecutionStore() {
        RobotActionEvent event = event("arm-position-probe-1");
        when(probeCoordinator.route(event)).thenReturn(true);

        processor.onEvent(event);

        verify(executionStore, never()).applyEvent(event);
        verify(reportPublisher, never()).publish(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void businessEventStillUsesExistingExecutionStateMachine() {
        RobotActionEvent event = event("business-action-1");
        when(probeCoordinator.route(event)).thenReturn(false);
        when(executionStore.applyEvent(event)).thenReturn(Optional.empty());

        processor.onEvent(event);

        verify(executionStore).applyEvent(event);
    }

    @Test
    void disconnectFailsPendingProbeBeforeHoldingBusinessExecutions() {
        RobotSessionView session = new RobotSessionView("session-1", "R01", "ARM", "client-1",
                Collections.emptyMap(), Collections.emptySet(), Instant.EPOCH, Instant.EPOCH);
        when(executionStore.holdActiveExecutionsForRobot("R01", "ROBOT_CONNECTION_LOST",
                "动作执行期间机器人连接中断，物理结果无法确认", Instant.EPOCH))
                .thenReturn(Collections.emptyList());

        processor.onDisconnected(session);

        verify(probeCoordinator).failRobot("R01", "机器人连接中断，当前位置无法确认。");
    }

    private RobotActionEvent event(String actionInstanceId) {
        return new RobotActionEvent("ACTION_EVENT", "event-1", "session-1", "R01",
                actionInstanceId, "device-1", 1L, RobotActionEvent.State.RUNNING,
                null, null, null, null, Instant.EPOCH);
    }
}
