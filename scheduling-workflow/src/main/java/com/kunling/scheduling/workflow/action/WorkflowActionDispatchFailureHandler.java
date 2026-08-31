package com.kunling.scheduling.workflow.action;

import com.kunling.scheduling.workflow.entity.FlowNode;
import com.kunling.scheduling.workflow.enums.NodeState;
import com.kunling.scheduling.workflow.mapper.FlowNodeMapper;
import com.kunling.scheduling.workflow.order.domain.OrderTask;
import com.kunling.scheduling.workflow.order.domain.OrderTaskStatus;
import com.kunling.scheduling.workflow.order.infrastructure.OrderTaskMapper;
import com.kunling.scheduling.workflow.service.WorkflowStateService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 处理流程数据已提交、但 Action 命令未能成功提交的场景。
 *
 * <p>此时不能删除流程节点，应保留执行身份并挂起流程，由人工修正
 * Action 定义、机器人连接或能力注册后再恢复。</p>
 */
@Service
public class WorkflowActionDispatchFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowActionDispatchFailureHandler.class);
    private static final String ERROR_CODE = "ACTION_DISPATCH_FAILED";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final FlowNodeMapper flowNodeMapper;
    private final OrderTaskMapper orderTaskMapper;
    private final WorkflowStateService workflowStateService;

    public WorkflowActionDispatchFailureHandler(FlowNodeMapper flowNodeMapper,
                                                OrderTaskMapper orderTaskMapper,
                                                WorkflowStateService workflowStateService) {
        this.flowNodeMapper = flowNodeMapper;
        this.orderTaskMapper = orderTaskMapper;
        this.workflowStateService = workflowStateService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(String actionInstanceId, RuntimeException exception) {
        FlowNode flowNode = flowNodeMapper.selectByActionInstanceIdForUpdate(actionInstanceId);
        if (flowNode == null) {
            log.error("Action 下发失败且未找到流程节点，actionInstanceId={}",
                    actionInstanceId, exception);
            return;
        }
        if (flowNode.getStatus() != NodeState.RUNNING) {
            log.warn("Action 下发失败时节点已不在运行态，忽略重复处理，actionInstanceId={}, state={}",
                    actionInstanceId, flowNode.getStatus());
            return;
        }

        flowNode.setStatus(NodeState.HANG);
        if (flowNodeMapper.updateById(flowNode) != 1) {
            throw new IllegalStateException("下发失败后流程节点挂起保存失败: " + flowNode.getId());
        }

        OrderTask task = orderTaskMapper.selectById(flowNode.getTaskId());
        if (task == null) {
            throw new IllegalStateException("下发失败后未找到订单任务: " + flowNode.getTaskId());
        }
        task.setStatus(OrderTaskStatus.QUEUED);
        task.setErrorCode(ERROR_CODE);
        task.setErrorMessage(safeMessage(exception));
        if (orderTaskMapper.updateById(task) != 1) {
            throw new IllegalStateException("下发失败后订单任务状态保存失败: " + task.getId());
        }

        try {
            workflowStateService.suspend(flowNode.getProcessInstanceId());
        } catch (RuntimeException suspendException) {
            // 流程可能已被其他管理动作终止，仍需提交上面的节点失败证据。
            log.error("Action 下发失败后挂起 Flowable 实例失败，processInstanceId={}",
                    flowNode.getProcessInstanceId(), suspendException);
        }
        log.error("Action 命令提交失败，流程已转人工恢复，actionInstanceId={}, actionDefinitionId={}",
                actionInstanceId, flowNode.getActionDefinitionId(), exception);
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception == null ? null : StringUtils.trimToNull(exception.getMessage());
        String normalized = StringUtils.defaultIfBlank(message, "Action 命令提交失败");
        return StringUtils.abbreviate(normalized, MAX_ERROR_MESSAGE_LENGTH);
    }
}
