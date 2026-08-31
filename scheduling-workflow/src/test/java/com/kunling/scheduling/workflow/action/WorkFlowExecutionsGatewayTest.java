package com.kunling.scheduling.workflow.action;

import com.kunling.scheduling.action.execution.application.ActionExecutionGateway;
import com.kunling.scheduling.action.execution.application.ActionExecutionReceipt;
import com.kunling.scheduling.action.execution.application.ExecuteActionCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkFlowExecutionsGatewayTest {

    @Test
    void forwardsOnlyThreeFieldExecutionCommand() {
        ActionExecutionGateway actionGateway = mock(ActionExecutionGateway.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ActionExecutionGateway> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(actionGateway);
        WorkFlowExecutionsGateway gateway = new WorkFlowExecutionsGateway(provider);
        ExecuteActionCommand command = new ExecuteActionCommand(
                "action-instance-1", "action-definition-1", "R01");
        ActionExecutionReceipt receipt = new ActionExecutionReceipt("action-instance-1");
        when(actionGateway.execute(command)).thenReturn(receipt);

        ActionExecutionReceipt actual = gateway.execute(command);

        assertSame(receipt, actual);
        verify(actionGateway).execute(same(command));
    }
}
