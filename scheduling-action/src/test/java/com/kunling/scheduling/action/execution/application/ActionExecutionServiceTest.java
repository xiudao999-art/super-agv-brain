package com.kunling.scheduling.action.execution.application;

import com.kunling.scheduling.action.ActionTestFixtures;
import com.kunling.scheduling.action.definition.application.ActionDefinitionService;
import com.kunling.scheduling.action.execution.application.ActionExecutionPreparationService.PreparedExecution;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import com.kunling.scheduling.action.robotbridge.application.ActionCapabilityValidator;
import com.kunling.scheduling.action.robotbridge.application.DispatchReceipt;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionExecutionServiceTest {
    private final ActionDefinitionService definitionService = mock(ActionDefinitionService.class);
    private final ActionPackageAssembler assembler = mock(ActionPackageAssembler.class);
    private final ActionExecutionPreparationService preparation = mock(ActionExecutionPreparationService.class);
    private final ActionExecutionStore store = mock(ActionExecutionStore.class);
    private final RobotActionTransport transport = mock(RobotActionTransport.class);
    private final ActionCapabilityValidator capabilityValidator = mock(ActionCapabilityValidator.class);
    private final ActionExecutionReportPublisher publisher = mock(ActionExecutionReportPublisher.class);
    private final ActionExecutionService service = new ActionExecutionService(
            definitionService, assembler, preparation, store, transport,
            capabilityValidator, publisher, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    @Test
    void newlyCreatedInstanceDispatchesExactlyOneCommand() {
        ActionExecutionView execution = pending();
        when(preparation.prepare(any())).thenReturn(new PreparedExecution(true, execution, preview()));
        when(transport.dispatch(any())).thenReturn(new DispatchReceipt("session-1", "message-1", Instant.EPOCH));

        assertThat(service.execute(command()).actionInstanceId()).isEqualTo("action-1");
        verify(transport).dispatch(any());
        verify(store).markDispatched("action-1", "session-1", "message-1", Instant.EPOCH);
    }

    @Test
    void repeatedInstanceReturnsReceiptWithoutDispatch() {
        when(preparation.prepare(any())).thenReturn(new PreparedExecution(false, pending(), null));

        assertThat(service.execute(command()).actionInstanceId()).isEqualTo("action-1");
        verify(transport, never()).dispatch(any());
    }

    @Test
    void uncertainDispatchEntersUnknownHoldAndPublishesReport() {
        when(preparation.prepare(any())).thenReturn(new PreparedExecution(true, pending(), preview()));
        when(transport.dispatch(any())).thenThrow(new RobotUnavailableException("写入失败"));
        ActionExecutionView held = ActionTestFixtures.execution(
                ActionExecutionState.UNKNOWN_HOLD, PhysicalOutcome.UNKNOWN,
                ActionTestFixtures.MAPPER.createObjectNode().put("message", "写入失败"));
        when(store.hold("action-1", "DISPATCH_RESULT_UNKNOWN", "写入失败", Instant.EPOCH))
                .thenReturn(held);

        service.execute(command());
        verify(publisher).publishLocalState(held, Instant.EPOCH);
    }

    private ExecuteActionCommand command() {
        return new ExecuteActionCommand("action-1", "definition-1", "R01");
    }

    private ActionExecutionView pending() {
        return ActionTestFixtures.execution(
                ActionExecutionState.DISPATCH_PENDING, PhysicalOutcome.NOT_STARTED, null);
    }

    private ActionPackagePreview preview() {
        return new ActionPackagePreview("definition-1", "package-hash", 60_000,
                ActionTestFixtures.executionPlan());
    }
}
