package com.kunling.scheduling.action.execution.application;

import com.kunling.scheduling.action.ActionTestFixtures;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.definition.application.ActionDefinitionService;
import com.kunling.scheduling.action.definition.application.ActionDefinitionView;
import com.kunling.scheduling.action.execution.domain.CreateActionExecutionResult;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.robotbridge.application.ActionCapabilityValidator;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionExecutionPreparationServiceTest {
    private final ActionDefinitionService definitionService = mock(ActionDefinitionService.class);
    private final ActionPackageAssembler assembler = mock(ActionPackageAssembler.class);
    private final ActionExecutionStore store = mock(ActionExecutionStore.class);
    private final RobotActionTransport transport = mock(RobotActionTransport.class);
    private final ActionCapabilityValidator capabilityValidator = mock(ActionCapabilityValidator.class);
    private final JsonCodec codec = new JsonCodec(ActionTestFixtures.MAPPER);
    private final ActionExecutionPreparationService service = new ActionExecutionPreparationService(
            definitionService, assembler, store, transport, capabilityValidator, codec,
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(definitionService.lockEnabledForExecution("definition-1"))
                .thenReturn(new ActionDefinitionView(
                        ActionTestFixtures.definition("definition-1", true), false,
                        null, Instant.EPOCH, Instant.EPOCH));
    }

    @Test
    void locksDefinitionBeforeCheckingAndCreatingExecution() {
        when(store.find("action-1")).thenReturn(Optional.empty());
        when(transport.findSession("R01")).thenReturn(Optional.of(ActionTestFixtures.session()));
        ActionPackagePreview preview = new ActionPackagePreview("definition-1", "package-hash",
                60_000, ActionTestFixtures.executionPlan());
        when(assembler.assemble(any())).thenReturn(preview);
        when(store.createIfAbsent(any())).thenAnswer(call -> new CreateActionExecutionResult(
                true, ActionTestFixtures.execution(
                com.kunling.scheduling.action.execution.domain.ActionExecutionState.DISPATCH_PENDING,
                com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome.NOT_STARTED, null)));

        assertThat(service.prepare(command()).created()).isTrue();
        InOrder order = inOrder(definitionService, store);
        order.verify(definitionService).lockEnabledForExecution("definition-1");
        order.verify(store).find("action-1");
        order.verify(store).createIfAbsent(any());
    }

    @Test
    void sameInstanceBoundToAnotherDefinitionIsRejected() {
        ActionExecutionView existing = new ActionExecutionView("action-1", "definition-other", "R01", "dc-1",
                "2.0", "request", "package", com.kunling.scheduling.action.execution.domain.ActionExecutionState.RUNNING,
                com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome.UNKNOWN, 60_000,
                ActionTestFixtures.executionPlan(), null, null, null, null, null, null, null, null,
                Instant.EPOCH, Instant.EPOCH, null);
        when(store.find("action-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.prepare(command()))
                .isInstanceOf(ActionConflictException.class)
                .hasMessageContaining("不同的 Action 定义");
    }

    @Test
    void persistedCommandInputAndHashComeFromTheSamePackage() {
        when(store.find("action-1")).thenReturn(Optional.empty());
        when(transport.findSession("R01")).thenReturn(Optional.of(ActionTestFixtures.session()));
        ActionPackagePreview preview = new ActionPackagePreview("definition-1", "package-hash",
                60_000, ActionTestFixtures.executionPlan());
        when(assembler.assemble(any())).thenReturn(preview);
        when(store.createIfAbsent(any())).thenReturn(new CreateActionExecutionResult(
                true, ActionTestFixtures.execution(
                com.kunling.scheduling.action.execution.domain.ActionExecutionState.DISPATCH_PENDING,
                com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome.NOT_STARTED, null)));

        service.prepare(command());
        ArgumentCaptor<com.kunling.scheduling.action.execution.domain.NewActionExecution> captor =
                ArgumentCaptor.forClass(com.kunling.scheduling.action.execution.domain.NewActionExecution.class);
        verify(store).createIfAbsent(captor.capture());
        assertThat(captor.getValue().packageHash()).isEqualTo("package-hash");
        assertThat(captor.getValue().commandInput()).isEqualTo(ActionTestFixtures.executionPlan());
    }

    private ExecuteActionCommand command() {
        return new ExecuteActionCommand("action-1", "definition-1", "R01");
    }
}
