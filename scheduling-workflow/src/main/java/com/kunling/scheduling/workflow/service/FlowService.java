package com.kunling.scheduling.workflow.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.workflow.dto.ExceptionCallBackDto;
import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.entity.FlowNode;
import com.kunling.scheduling.workflow.enums.NodeState;
import com.kunling.scheduling.workflow.enums.NodeStateEnum;
import com.kunling.scheduling.workflow.enums.StartTypeEnum;
import com.kunling.scheduling.workflow.mapper.FlowNodeMapper;
import com.kunling.scheduling.workflow.order.domain.OrderTask;
import com.kunling.scheduling.workflow.order.domain.OrderTaskStatus;
import com.kunling.scheduling.workflow.order.infrastructure.OrderTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class FlowService {

    @Resource
    private FlowNodeMapper flowNodeMapper;

    @Resource
    private OrderTaskMapper orderTaskMapper;

    @Resource
    private WorkflowStateService workflowStateService;

    @Resource
    private FlowControlService flowControlService;

    @Resource
    private WorkflowService workflowService;

    @Transactional
    public void dealExceptionCallBack(ExceptionCallBackDto dto) {
        if (dto == null || dto.getNodeId() == null) {
            throw new IllegalArgumentException("taskId不能为空");
        }
        if (dto.getDealStatus() == null || dto.getDealStatus().trim().isEmpty()) {
            throw new IllegalArgumentException("dealStatus不能为空");
        }

        NodeState dealStatus;
        try {
            dealStatus = NodeState.valueOf(dto.getDealStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的异常恢复状态: " + dto.getDealStatus(), ex);
        }
     /*   if (dealStatus != NodeState.SUCCEEDED && dealStatus != NodeState.SKIPPED) {
            throw new IllegalArgumentException("异常恢复状态仅支持SUCCEEDED或SKIPPED");
        }*/
        FlowNode currentNode = flowNodeMapper.selectById(dto.getNodeId());

        if (currentNode == null) {
            throw new NoSuchElementException("任务没有可恢复的流程节点: " + dto.getNodeId());
        }


        if (currentNode.getProcessInstanceId() == null
                || currentNode.getProcessInstanceId().trim().isEmpty()) {
            throw new IllegalStateException("当前流程节点没有流程实例ID: " + currentNode.getId());
        }

        OrderTask orderTask = orderTaskMapper.selectById(currentNode.getTaskId());
        if (orderTask == null) {
            throw new NoSuchElementException("关联的订单任务不存在: " + currentNode.getTaskId());
        }

        currentNode.setStatus(dealStatus);
        if (flowNodeMapper.updateById(currentNode) != 1) {
            throw new IllegalStateException("更新当前流程节点失败: " + currentNode.getId());
        }

        //根据回传状态进行逻辑判断 取消状态则撤销当前流程
        if (dto.getDealStatus().equals(NodeState.SKIPPED.name())) {
            workflowStateService.terminate(currentNode.getProcessInstanceId(), "严重错误终止流程，尚未启动的节点统一取消");
            List<FlowNode> pendingNodes = flowNodeMapper.selectList(Wrappers.<FlowNode>lambdaQuery()
                    .eq(FlowNode::getTaskId, currentNode.getTaskId()));
            for (FlowNode pendingNode : pendingNodes) {
                pendingNode.setStatus(NodeState.CANCELLED);
            }
            if (!pendingNodes.isEmpty()) {
                flowNodeMapper.updateById(pendingNodes);
                if (orderTask == null) {
                    throw new IllegalArgumentException("订单任务不存在: " + orderTask.getId());
                }
                orderTask.setStatus(OrderTaskStatus.CANCELLED);
                orderTaskMapper.updateById(orderTask);
            }
            log.error("流程因严重错误终止: flowId={}, nodeId={}",
                    currentNode.getTaskId(), currentNode.getId());

            return;
        }
        orderTask.setStatus(OrderTaskStatus.RUNNING);
        orderTask.setCompletedAt(null);
        orderTask.setErrorCode(null);
        orderTask.setErrorMessage(null);
        if (orderTaskMapper.updateById(orderTask) != 1) {
            throw new IllegalStateException("更新订单任务失败: " + orderTask.getId());
        }
        workflowStateService.activate(currentNode.getProcessInstanceId());
        List<WorkflowResponses.ActiveNode> activeNodes = workflowService.listActiveNodes(currentNode.getProcessInstanceId());
        Map<String, Object> variables = new HashMap<>();
        variables.put("executionId", activeNodes.get(0).getExecutionId());
        variables.put("success", true);
        variables.put("nodeState", NodeStateEnum.SUCCEEDED.name());
        variables.put("deviceStatus", "COMPLETED");

        //如果为跳过,则结束当前节点,如果为running则继续进行当前节点
        if (dto.getDealStatus().equals(NodeState.SKIPPED.name())) {
            WorkflowResponses.Instance instance = workflowStateService.completeExecution(activeNodes.get(0).getExecutionId(), variables);
        }
        //执行下一个节点流程
        List<WorkflowResponses.ActiveNode> current = workflowService.listActiveNodes(currentNode.getProcessInstanceId());
        flowControlService.dispatchDownstreamAction(currentNode.getProcessInstanceId(), currentNode.getTaskId(),
                current.get(0), StartTypeEnum.CALLBACK);

    }
}
