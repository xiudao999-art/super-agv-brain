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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

@Service
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
        if (dealStatus != NodeState.SUCCEEDED && dealStatus != NodeState.SKIPPED) {
            throw new IllegalArgumentException("异常恢复状态仅支持SUCCEEDED或SKIPPED");
        }
        FlowNode currentNode = flowNodeMapper.selectById(dto.getNodeId());

        if (currentNode==null) {
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

        orderTask.setStatus(OrderTaskStatus.RUNNING);
        orderTask.setCompletedAt(null);
        orderTask.setErrorCode(null);
        orderTask.setErrorMessage(null);
        if (orderTaskMapper.updateById(orderTask) != 1) {
            throw new IllegalStateException("更新订单任务失败: " + orderTask.getId());
        }
        //恢复当前挂起的流程
        workflowStateService.activate(currentNode.getProcessInstanceId());
        List<WorkflowResponses.ActiveNode> activeNodes = workflowService.listActiveNodes(currentNode.getProcessInstanceId());
        Map<String, Object> variables = new HashMap<>();
        variables.put("executionId", activeNodes.get(0).getExecutionId());
        variables.put("success", true);
        variables.put("nodeState", NodeStateEnum.SUCCEEDED.name());
        variables.put("deviceStatus", "COMPLETED");
        WorkflowResponses.Instance instance = workflowStateService.completeExecution(activeNodes.get(0).getExecutionId(), variables);
        //执行下一个节点流程
        List<WorkflowResponses.ActiveNode> current = workflowService.listActiveNodes(currentNode.getProcessInstanceId());
        flowControlService.dispatchDownstreamAction(currentNode.getProcessInstanceId(), currentNode.getTaskId(),
                current.get(0), StartTypeEnum.START);

    }
}
