package com.kunling.scheduling.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.kunling.scheduling.action.execution.domain.ActionExecutionReport;
import com.kunling.scheduling.action.execution.domain.ActionExecutionResult;
import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.entity.FlowNode;
import com.kunling.scheduling.workflow.entity.NodeStateTransitionRule;
import com.kunling.scheduling.workflow.entity.RobotAlarmRecord;
import com.kunling.scheduling.workflow.enums.NodeState;
import com.kunling.scheduling.workflow.enums.NodeStateEnum;
import com.kunling.scheduling.workflow.enums.StartTypeEnum;
import com.kunling.scheduling.workflow.mapper.NodeStateTransitionRuleMapper;
import com.kunling.scheduling.workflow.mapper.RobotAlarmRecordMapper;
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

    @Resource
    private RobotAlarmRecordMapper robotAlarmRecordMapper;


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
    public void statusChanged(ActionExecutionReport report) {
        if (report == null || report.actionInstanceId() == null
                || report.actionInstanceId().trim().isEmpty()) {
            throw new IllegalArgumentException("actionInstanceId 不能为空");
        }

        FlowNode flowNode = flowNodeService.lambdaQuery()
                .eq(FlowNode::getActionInstanceId, report.actionInstanceId())
                .last("limit 1")
                .one();
        if (flowNode == null) {
            throw new IllegalArgumentException(
                    "未找到 actionInstanceId 对应的流程节点: " + report.actionInstanceId());
        }
        Long nodeId = flowNode.getId();


        //模拟执行过程
  /*      try {
            if (flowNode.getNodeCode().contains("MOVE")) {
                Thread.sleep(120000);
            } else {
                Thread.sleep(30000);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }*/

        String eventCode;
        if (report.result() == ActionExecutionResult.SUCCEEDED) {
            eventCode = "SUCCEEDED";
        } else {
            if (report.failure() == null || report.failure().handlingConstraint() == null) {
                throw new IllegalArgumentException("失败报告缺少 handlingConstraint");
            }
            eventCode = report.failure().handlingConstraint().name();
        }


        NodeStateTransitionRule rule = this.lambdaQuery().eq(NodeStateTransitionRule::getCurrentState, flowNode.getStatus())
                .eq(NodeStateTransitionRule::getEventCode, eventCode).last("limit 1").one();
        if (rule == null) {
            throw new IllegalStateException("未配置节点状态转换规则: currentState="
                    + flowNode.getStatus() + ", eventCode=" + eventCode);
        }

        List<WorkflowResponses.ActiveNode> activeNodes = workflowService.listActiveNodes(flowNode.getProcessInstanceId());


        if (CollectionUtils.isEmpty(activeNodes)) {
            log.warn("回传事件后未找到运行中的流程实例，processInstanceId={}", flowNode.getProcessInstanceId());
            return;
        }
        switch (eventCode) {
            case "SUCCEEDED":
                handleSucceeded(flowNode, activeNodes.get(0));
                break;
            case "RETRYABLE":
                handleRetryable(flowNode, activeNodes.get(0));
                break;
            case "MANUAL_INTERVENTION":
                // 维持当前节点状态，等待人工接口再次触发。
                handleManual(flowNode);
                log.warn("节点需要人工介入，保持当前状态: nodeId={}, state={}",
                        flowNode.getId(), flowNode.getStatus());
                break;
            case "NON_RETRYABLE":
                //不可重试失败也同样挂起处理,等待外部唤醒
                handleManual(flowNode);
                log.warn("节点发生不可重试失败，等待外部处理: nodeId={}", flowNode.getId());
                break;
            case "CRITICAL":
                handleCritical(flowNode);
                break;
            default:
                throw new IllegalArgumentException("不支持的节点事件状态: " + eventCode);
        }
        updateNodeState(flowNode, rule.getNextState());
        //增加异常日志
        if (report.result() != ActionExecutionResult.SUCCEEDED) {
            RobotAlarmRecord record = new RobotAlarmRecord();
            record.setNodeId(nodeId);
            record.setAlarmNo(report.failure().businessCode());
            record.setAlarmDescription(report.failure().message());
            record.setHandlingLevel(3);
            record.setHandlingStatus(0);
            robotAlarmRecordMapper.insert(record);
        }
    }

    private void handleSucceeded(FlowNode currentNode, WorkflowResponses.ActiveNode activeNode) {
        // 终态回调可能被重复投递，已成功的节点不能再次启动下一节点。
        Map<String, Object> variables = new HashMap<>();
        variables.put("executionId", activeNode.getExecutionId());
        variables.put("success", true);
        variables.put("nodeState", NodeStateEnum.SUCCEEDED.name());
        variables.put("deviceStatus", "COMPLETED");
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
