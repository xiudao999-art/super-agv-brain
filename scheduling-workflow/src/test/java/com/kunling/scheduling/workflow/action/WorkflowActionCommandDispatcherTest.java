package com.kunling.scheduling.workflow.action;

import com.kunling.scheduling.action.execution.application.ExecuteActionCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

class WorkflowActionCommandDispatcherTest {

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void dispatchesOnlyAfterWorkflowTransactionCommits() {
        WorkFlowExecutionsGateway gateway = mock(WorkFlowExecutionsGateway.class);
        WorkflowActionDispatchFailureHandler failureHandler =
                mock(WorkflowActionDispatchFailureHandler.class);
        WorkflowActionCommandDispatcher dispatcher =
                new WorkflowActionCommandDispatcher(gateway, failureHandler);
        ExecuteActionCommand command = command();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatchAfterCommit(command);

        verify(gateway, never()).execute(command);
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        verify(gateway).execute(same(command));
        verify(failureHandler, never()).handle(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(RuntimeException.class));
    }

    @Test
    void recordsDispatchFailureAfterCommit() {
        WorkFlowExecutionsGateway gateway = mock(WorkFlowExecutionsGateway.class);
        WorkflowActionDispatchFailureHandler failureHandler =
                mock(WorkflowActionDispatchFailureHandler.class);
        WorkflowActionCommandDispatcher dispatcher =
                new WorkflowActionCommandDispatcher(gateway, failureHandler);
        ExecuteActionCommand command = command();
        IllegalStateException failure = new IllegalStateException("定义未启用");
        doThrow(failure).when(gateway).execute(command);

        dispatcher.dispatchAfterCommit(command);

        verify(failureHandler).handle(command.actionInstanceId(), failure);
    }

    @Test
    void doesNotReportRollbackWhenPostCommitFailureHandlingAlsoFails() {
        WorkFlowExecutionsGateway gateway = mock(WorkFlowExecutionsGateway.class);
        WorkflowActionDispatchFailureHandler failureHandler =
                mock(WorkflowActionDispatchFailureHandler.class);
        WorkflowActionCommandDispatcher dispatcher =
                new WorkflowActionCommandDispatcher(gateway, failureHandler);
        ExecuteActionCommand command = command();
        IllegalStateException dispatchFailure = new IllegalStateException("定义未启用");
        doThrow(dispatchFailure).when(gateway).execute(command);
        doThrow(new IllegalStateException("人工恢复状态保存失败"))
                .when(failureHandler).handle(command.actionInstanceId(), dispatchFailure);

        assertDoesNotThrow(() -> dispatcher.dispatchAfterCommit(command));
    }

    private ExecuteActionCommand command() {
        return new ExecuteActionCommand("action-instance-1", "action-definition-1", "R01");
    }
}
