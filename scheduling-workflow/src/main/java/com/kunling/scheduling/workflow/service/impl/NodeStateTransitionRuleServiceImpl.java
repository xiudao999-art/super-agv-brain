package com.kunling.scheduling.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.kunling.scheduling.workflow.dto.StatusChangedDto;
import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.entity.FlowNode;
import com.kunling.scheduling.workflow.entity.NodeStateTransitionRule;
import com.kunling.scheduling.workflow.enums.NodeState;
import com.kunling.scheduling.workflow.enums.NodeStateEnum;
import com.kunling.scheduling.workflow.enums.StartTypeEnum;
import com.kunling.scheduling.workflow.mapper.NodeStateTransitionRuleMapper;
import com.kunling.scheduling.workflow.order.application.OrderTaskOrchestrationService;
import com.kunling.scheduling.workflow.order.domain.CustomerOrder;
import com.kunling.scheduling.workflow.order.domain.OrderStatus;
import com.kunling.scheduling.workflow.order.domain.OrderTask;
import com.kunling.scheduling.workflow.order.domain.OrderTaskStatus;
import com.kunling.scheduling.workflow.order.infrastructure.CustomerOrderMapper;
import com.kunling.scheduling.workflow.order.infrastructure.OrderTaskMapper;
import com.kunling.scheduling.workflow.service.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.criterion.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NodeStateTransitionRuleServiceImpl
        extends ServiceImpl<NodeStateTransitionRuleMapper, NodeStateTransitionRule>
        implements NodeStateTransitionRuleService {

    @Resource
    private FlowNodeService flowNodeService;

    @Resource
    private WorkflowStateService workflowStateService;

    @Resource
    private WorkflowService workflowService;

    @Resource
    private FlowControlService flowControlService;

    @Resource
    private OrderTaskMapper orderTaskMapper;

    @Resource
    private CustomerOrderMapper customerOrderMapper;

    @Resource
    private OrderTaskOrchestrationService orderTaskOrchestrationService;


    @Override
    public List<NodeStateTransitionRule> listRules(String ruleSetCode, String currentState,
                                                   String eventCode, Integer enabled) {
        return list(Wrappers.<NodeStateTransitionRule>lambdaQuery()
                .eq(ruleSetCode != null && !ruleSetCode.isEmpty(),
                        NodeStateTransitionRule::getRuleSetCode, ruleSetCode)
                .eq(currentState != null && !currentState.isEmpty(),
                        NodeStateTransitionRule::getCurrentState, currentState)
                .eq(eventCode != null && !eventCode.isEmpty(),
                        NodeStateTransitionRule::getEventCode, eventCode)
                .eq(enabled != null, NodeStateTransitionRule::getEnabled, enabled)
                .orderByAsc(NodeStateTransitionRule::getId));
    }

    @Override
    public NodeStateTransitionRule getRule(Long id) {
        NodeStateTransitionRule rule = getById(id);
        if (rule == null) {

        }
        return rule;
    }

    @Override
    @Transactional
    public NodeStateTransitionRule createRule(NodeStateTransitionRule rule) {
        rule.setId(null);
        save(rule);
        return getRule(rule.getId());
    }

    @Override
    @Transactional
    public NodeStateTransitionRule updateRule(Long id, NodeStateTransitionRule rule) {
        getRule(id);
        rule.setId(id);
        updateById(rule);
        return getRule(id);
    }

    @Override
    @Transactional
    public void deleteRule(Long id) {
        getRule(id);
        removeById(id);
    }

    @Override
    @Transactional
    public void statusChanged(StatusChangedDto dto) {
        if (dto == null || dto.getWorkflowNodeInstanceId() == null
                || dto.getWorkflowNodeInstanceId().trim().isEmpty()) {
            throw new IllegalArgumentException("workflowNodeInstanceId 不能为空");
        }
        if (dto.getEventCode() == null || dto.getEventCode().trim().isEmpty()) {
            throw new IllegalArgumentException("eventCode 不能为空");
        }

        Integer nodeId = Integer.valueOf(dto.getWorkflowNodeInstanceId());
        FlowNode flowNode = flowNodeService.getById(nodeId);
        if (flowNode == null) {
            throw new IllegalArgumentException("流程节点不存在: " + nodeId);
        }

        //模拟执行过程
        try {
            if (flowNode.getNodeCode().contains("MOVE")) {
                Thread.sleep(120000);
            } else {
                Thread.sleep(30000);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        String eventCode = dto.getEventCode().trim().toUpperCase(java.util.Locale.ROOT);
        log.info("处理节点状态回调: nodeId={}, currentState={}, eventCode={}",
                flowNode.getId(), flowNode.getStatus(), eventCode);

        NodeStateTransitionRule rule = this.lambdaQuery().eq(NodeStateTransitionRule::getCurrentState, flowNode.getStatus())
                .eq(NodeStateTransitionRule::getEventCode, dto.getEventCode()).last("limit 1").one();

        List<WorkflowResponses.ActiveNode> activeNodes = workflowService.listActiveNodes(flowNode.getProcessInstanceId());


        if (CollectionUtils.isEmpty(activeNodes)) {
            log.warn("回传事件后未找到运行中的流程实例，processInstanceId={}", flowNode.getProcessInstanceId());
            return;
        }
        WorkflowResponses.ActiveNode activeNode = activeNodes.get(0);
        boolean routeException = !"SUCCEEDED".equals(eventCode)
                && workflowService.hasExceptionGatewayAfter(flowNode.getProcessInstanceId(), activeNode.getActivityId());
        if (routeException) {
            handleExceptionBranch(flowNode, activeNode, dto);
        } else {
            switch (eventCode) {
                case "SUCCEEDED":
                    handleSucceeded(flowNode, activeNode, dto);
                    break;
                case "RETRYABLE":
                    handleRetryable(flowNode, activeNode);
                    break;
                case "MANUAL_INTERVENTION":
                    handleManual(flowNode);
                    log.warn("节点需要人工介入，保持当前状态: nodeId={}, state={}", flowNode.getId(), flowNode.getStatus());
                    break;
                case "NON_RETRYABLE":
                    handleManual(flowNode);
                    log.warn("节点发生不可重试失败，等待外部处理: nodeId={}", flowNode.getId());
                    break;
                case "CRITICAL":
                    handleCritical(flowNode);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的节点事件状态: " + eventCode);
            }
        }
        updateNodeState(flowNode, rule.getNextState());
    }

    private void handleSucceeded(FlowNode currentNode, WorkflowResponses.ActiveNode activeNode, StatusChangedDto dto) {
        // 终态回调可能被重复投递，已成功的节点不能再次启动下一节点。
        Map<String, Object> variables = completionVariables(activeNode, dto, true);
        WorkflowResponses.Instance instance = workflowStateService.completeExecution(activeNode.getExecutionId(), variables);
        //完成当前节点后,判断流程是否结束
        if ("COMPLETED".equals(instance.getState())) {
            updateOrderTask(currentNode.getTaskId(), OrderTaskStatus.SUCCEEDED);
            log.info("当前任务{}的流程全部完成", currentNode.getTaskId());
            //判断订单下的任务是否都完成
            OrderTask orderTask = orderTaskMapper.selectById(currentNode.getTaskId());
            List<OrderTask> orderTasks = orderTaskMapper.selectList(Wrappers.<OrderTask>lambdaQuery().eq(OrderTask::getOrderId, orderTask.getOrderId()));
            //
            List<OrderTask> unCompleteTask = orderTasks.stream().filter(s -> !OrderTaskStatus.SUCCEEDED.equals(s.getStatus())).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(unCompleteTask)) {
                CustomerOrder customerOrder = new CustomerOrder();
                customerOrder.setId(orderTask.getOrderId());
                customerOrder.setStatus(OrderStatus.SUCCEEDED);
                customerOrderMapper.updateById(customerOrder);
            } else {
                //有下一个任务,执行新的流程
                orderTaskOrchestrationService.dispatchNext();
            }

        } else {
            //未完成的话,执行下发下一节点
            List<WorkflowResponses.ActiveNode> nextNodes = workflowService.listActiveNodes(instance.getId());
            if (CollectionUtils.isEmpty(nextNodes)) {
                throw new IllegalStateException("流程未结束但没有找到下一个活动节点: " + instance.getId());
            }
            flowControlService.dispatchDownstreamAction(currentNode.getProcessInstanceId(), currentNode.getTaskId(),
                    nextNodes.get(0), StartTypeEnum.START);
        }

    }

    private void handleExceptionBranch(FlowNode currentNode, WorkflowResponses.ActiveNode activeNode,
                                       StatusChangedDto dto) {
        Map<String, Object> variables = completionVariables(activeNode, dto, false);
        WorkflowResponses.Instance instance = workflowStateService.completeExecution(activeNode.getExecutionId(), variables);
        if ("COMPLETED".equals(instance.getState())) {
            updateOrderTask(currentNode.getTaskId(), OrderTaskStatus.FAILED);
            throw new IllegalStateException("异常分支不能直接结束流程: " + activeNode.getActivityId());
        }
        List<WorkflowResponses.ActiveNode> nextNodes = workflowService.listActiveNodes(instance.getId());
        if (CollectionUtils.isEmpty(nextNodes)) {
            throw new IllegalStateException("异常分支没有找到恢复动作: " + instance.getId());
        }
        flowControlService.dispatchDownstreamAction(currentNode.getProcessInstanceId(), currentNode.getTaskId(),
                nextNodes.get(0), StartTypeEnum.CALLBACK);
        log.info("动作异常已按模板分支推进: nodeId={}, eventCode={}, businessCode={}, reasonCode={}",
                currentNode.getId(), dto.getEventCode(), dto.getBusinessCode(), dto.getReasonCode());
    }

    private Map<String, Object> completionVariables(WorkflowResponses.ActiveNode activeNode,
                                                    StatusChangedDto dto, boolean success) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("executionId", activeNode.getExecutionId());
        variables.put("success", success);
        variables.put("nodeState", success ? NodeStateEnum.SUCCEEDED.name() : NodeStateEnum.FAILED.name());
        variables.put("deviceStatus", success ? "COMPLETED" : "FAILED");
        Map<String, Object> actionResult = new LinkedHashMap<>();
        actionResult.put("success", success);
        actionResult.put("eventCode", dto.getEventCode());
        actionResult.put("businessCode", dto.getBusinessCode());
        actionResult.put("reasonCode", dto.getReasonCode());
        actionResult.put("physicalOutcome", dto.getPhysicalOutcome());
        actionResult.put("actionKey", dto.getActionKey());
        actionResult.put("actionInstanceId", dto.getActionInstanceId());
        variables.put("actionResult", actionResult);
        return variables;
    }

    private void updateOrderTask(Long taskId, OrderTaskStatus orderTaskStatus) {
        OrderTask orderTask = orderTaskMapper.selectById(taskId);
        if (orderTask == null) {
            throw new IllegalArgumentException("订单任务不存在: " + taskId);
        }
        orderTask.setStatus(orderTaskStatus);
        if (orderTaskStatus == OrderTaskStatus.SUCCEEDED) {
            orderTask.setCompletedAt(LocalDateTime.now());
        } else if (orderTaskStatus == OrderTaskStatus.FAILED) {
            orderTask.setCompletedAt(LocalDateTime.now());
        } else if (orderTaskStatus == OrderTaskStatus.CANCELLED) {
            orderTask.setCompletedAt(LocalDateTime.now());
        }
        orderTaskMapper.updateById(orderTask);
    }

    private void handleRetryable(FlowNode currentNode, WorkflowResponses.ActiveNode activeNode) {
        if (currentNode.getStatus() != NodeState.RUNNING
                && currentNode.getStatus() != NodeState.WAITING) {
            throw new IllegalStateException("当前节点状态不允许重试: " + currentNode.getStatus());
        }
        flowControlService.dispatchDownstreamAction(currentNode.getProcessInstanceId(), currentNode.getTaskId(), activeNode, StartTypeEnum.START);

    }

    //将当前节点任务挂起
    private void handleManual(FlowNode currentNode) {
        WorkflowResponses.Instance suspend = workflowStateService.suspend(currentNode.getProcessInstanceId());
      // updateOrderTask(currentNode.getTaskId(), OrderTaskStatus.FAILED);
    }

    private void handleCritical(FlowNode currentNode) {
        // 严重错误终止流程，尚未启动的节点统一取消。
        workflowStateService.terminate(currentNode.getProcessInstanceId(), "严重错误终止流程，尚未启动的节点统一取消");
        List<FlowNode> pendingNodes = flowNodeService.list(Wrappers.<FlowNode>lambdaQuery()
                .eq(FlowNode::getTaskId, currentNode.getTaskId()));
        for (FlowNode pendingNode : pendingNodes) {
            pendingNode.setStatus(NodeState.CANCELLED);
        }
        if (!pendingNodes.isEmpty()) {
            flowNodeService.updateBatchById(pendingNodes);
            updateOrderTask(currentNode.getTaskId(), OrderTaskStatus.CANCELLED);
        }
        log.error("流程因严重错误终止: flowId={}, nodeId={}",
                currentNode.getTaskId(), currentNode.getId());
    }

    private void updateNodeState(FlowNode node, NodeState state) {
        node.setStatus(state);
        if (!flowNodeService.updateById(node)) {
            throw new IllegalStateException("流程节点状态更新失败: " + node.getId());
        }
    }

}
