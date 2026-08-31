package com.kunling.scheduling.workflow.action;

import com.kunling.scheduling.workflow.entity.FlowNode;
import com.kunling.scheduling.workflow.enums.NodeState;
import com.kunling.scheduling.workflow.mapper.FlowNodeMapper;
import com.kunling.scheduling.workflow.order.domain.OrderTask;
import com.kunling.scheduling.workflow.order.domain.OrderTaskStatus;
import com.kunling.scheduling.workflow.order.infrastructure.OrderTaskMapper;
import com.kunling.scheduling.workflow.service.WorkflowStateService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowActionDispatchFailureHandlerTest {

    @Test
    void hangsRunningNodeAndQueuesTaskForManualRecovery() {
        FlowNodeMapper nodeMapper = mock(FlowNodeMapper.class);
        OrderTaskMapper taskMapper = mock(OrderTaskMapper.class);
        WorkflowStateService stateService = mock(WorkflowStateService.class);
        WorkflowActionDispatchFailureHandler handler =
                new WorkflowActionDispatchFailureHandler(nodeMapper, taskMapper, stateService);
        FlowNode node = runningNode();
        OrderTask task = new OrderTask();
        task.setId(20L);
        task.setStatus(OrderTaskStatus.RUNNING);
        when(nodeMapper.selectByActionInstanceIdForUpdate("action-1")).thenReturn(node);
        when(nodeMapper.updateById(same(node))).thenReturn(1);
        when(taskMapper.selectById(20L)).thenReturn(task);
        when(taskMapper.updateById(same(task))).thenReturn(1);

        handler.handle("action-1", new IllegalStateException("定义未启用"));

        assertEquals(NodeState.HANG, node.getStatus());
        assertEquals(OrderTaskStatus.QUEUED, task.getStatus());
        assertEquals("ACTION_DISPATCH_FAILED", task.getErrorCode());
        verify(stateService).suspend("process-1");
    }

    @Test
    void ignoresLateFailureWhenNodeAlreadyLeftRunningState() {
        FlowNodeMapper nodeMapper = mock(FlowNodeMapper.class);
        OrderTaskMapper taskMapper = mock(OrderTaskMapper.class);
        WorkflowStateService stateService = mock(WorkflowStateService.class);
        WorkflowActionDispatchFailureHandler handler =
                new WorkflowActionDispatchFailureHandler(nodeMapper, taskMapper, stateService);
        FlowNode node = runningNode();
        node.setStatus(NodeState.SUCCEEDED);
        when(nodeMapper.selectByActionInstanceIdForUpdate("action-1")).thenReturn(node);

        handler.handle("action-1", new IllegalStateException("重复失败"));

        verify(nodeMapper, never()).updateById(node);
        verify(taskMapper, never()).selectById(20L);
        verify(stateService, never()).suspend("process-1");
    }

    private FlowNode runningNode() {
        FlowNode node = new FlowNode();
        node.setId(10L);
        node.setTaskId(20L);
        node.setProcessInstanceId("process-1");
        node.setActionDefinitionId("definition-1");
        node.setActionInstanceId("action-1");
        node.setStatus(NodeState.RUNNING);
        return node;
    }
}
