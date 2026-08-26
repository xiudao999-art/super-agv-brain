package com.kunling.scheduling.workflow.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.workflow.dto.ExceptionCallBackDto;
import com.kunling.scheduling.workflow.entity.FlowNode;
import com.kunling.scheduling.workflow.enums.NodeState;
import com.kunling.scheduling.workflow.mapper.FlowNodeMapper;
import com.kunling.scheduling.workflow.order.domain.OrderTask;
import com.kunling.scheduling.workflow.order.domain.OrderTaskStatus;
import com.kunling.scheduling.workflow.order.infrastructure.OrderTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
public class FlowService {

    @Resource
    private FlowNodeMapper flowNodeMapper;

    @Resource
    private OrderTaskMapper orderTaskMapper;

    @Resource
    private WorkflowStateService workflowStateService;

    @Transactional
    public void dealExceptionCallBack(ExceptionCallBackDto dto) {
        if (dto == null || dto.getTaskId() == null) {
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

        List<FlowNode> unfinishedNodes = flowNodeMapper.selectList(
                Wrappers.<FlowNode>lambdaQuery()
                        .eq(FlowNode::getTaskId, dto.getTaskId())
                        .notIn(FlowNode::getStatus,
                                Arrays.asList(NodeState.SUCCEEDED, NodeState.SKIPPED))
                        .orderByAsc(FlowNode::getSort));
        if (unfinishedNodes.isEmpty()) {
            throw new NoSuchElementException("任务没有可恢复的流程节点: " + dto.getTaskId());
        }

        FlowNode currentNode = unfinishedNodes.get(0);
        if (currentNode.getProcessInstanceId() == null
                || currentNode.getProcessInstanceId().trim().isEmpty()) {
            throw new IllegalStateException("当前流程节点没有流程实例ID: " + currentNode.getId());
        }

        OrderTask orderTask = orderTaskMapper.selectById(dto.getTaskId());
        if (orderTask == null) {
            throw new NoSuchElementException("关联的订单任务不存在: " + dto.getTaskId());
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

        workflowStateService.activate(currentNode.getProcessInstanceId());
    }
}
