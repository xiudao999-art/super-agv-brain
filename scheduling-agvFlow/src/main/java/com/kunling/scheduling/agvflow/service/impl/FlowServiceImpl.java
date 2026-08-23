package com.kunling.scheduling.agvflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.kunling.scheduling.agvflow.domain.dto.*;
import com.kunling.scheduling.agvflow.domain.entity.Flow;
import com.kunling.scheduling.agvflow.domain.entity.FlowNode;
import com.kunling.scheduling.agvflow.domain.entity.FlowTemplate;
import com.kunling.scheduling.agvflow.mapper.FlowMapper;
import com.kunling.scheduling.agvflow.enums.NodeState;
import com.kunling.scheduling.agvflow.enums.FlowState;
import com.kunling.scheduling.agvflow.service.FlowNodeService;
import com.kunling.scheduling.agvflow.service.FlowService;
import com.kunling.scheduling.agvflow.service.FlowTemplateService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class FlowServiceImpl extends ServiceImpl<FlowMapper, Flow>
        implements FlowService {

    @Resource
    private FlowTemplateService templateService;

    @Resource
    private FlowNodeService nodeService;

    @Override
    @Transactional
    public Long createFlow(FlowCreateRequest request) {
        FlowTemplate template = templateService.getById(request.getTemplateId());
        if (template == null) {
            throw new IllegalArgumentException("关联的流程模板不存在: " + request.getTemplateId());
        }
        //查询节点信息
        LambdaQueryWrapper<FlowNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowNode::getTemplateId,request.getTemplateId());
        wrapper.eq(FlowNode::getSort,2);
        FlowNode flowNode = nodeService.getOne(wrapper);
        if (flowNode == null){
            throw new IllegalArgumentException("节点不存在");
        }

        Flow flow = new Flow();
        flow.setCurrentNodeId(flowNode.getId());
        flow.setFlowName(request.getFlowName());
        flow.setTaskId(request.getTaskId());
        flow.setOrderNumber(request.getOrderNumber());
        flow.setTemplateId(request.getTemplateId());
        flow.setTemplateVersion(template.getVersion());
        flow.setFlowState(FlowState.PENDING);
        flow.setStartedAt(LocalDateTime.now());
        flow.setVersion(1);
        flow.setAttempt(0);
        FlowNode currentNode = resolveCurrentNode(request.getTemplateId(), request.getCurrentNodeId());
        flow.setCurrentNodeId(currentNode == null ? null : currentNode.getId());
        flow.setCurrentNodeState(request.getCurrentNodeState() == null ? NodeState.PENDING : request.getCurrentNodeState());
        save(flow);

        //调用下游接口执行接口
        return flow.getId();
    }

    @Override
    public FlowDetail getFlowDetail(Long id) {
        Flow flow = getById(id);
        if (flow == null) {
            throw new NoSuchElementException("流程不存在: " + id);
        }
        return toFlowDetail(flow);
    }

    @Override
    public Page<FlowListItem> pageFlows(int current, int size, String keyword) {
        Page<Flow> page = lambdaQuery()
                .and(StringUtils.isNotEmpty(keyword), wrapper -> wrapper
                        .like(Flow::getFlowName, keyword)
                        .or()
                        .like(Flow::getOrderNumber, keyword))
                .orderByDesc(Flow::getUpdateTime)
                .page(new Page<>(current, size));

        List<Flow> flows = page.getRecords();
        if (flows.isEmpty()) {
            return new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        }

        List<Long> templateIds = flows.stream()
                .map(Flow::getTemplateId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, FlowTemplate> templateMap = templateService.listByIds(templateIds)
                .stream()
                .collect(Collectors.toMap(FlowTemplate::getId, t -> t));

        Map<Long, Long> nodeCountMap = nodeService.list(Wrappers.<FlowNode>lambdaQuery()
                        .in(FlowNode::getTemplateId, templateIds))
                .stream()
                .collect(Collectors.groupingBy(FlowNode::getTemplateId, Collectors.counting()));

        List<Long> currentNodeIds = flows.stream()
                .map(Flow::getCurrentNodeId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, FlowNode> currentNodeMap = currentNodeIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : nodeService.listByIds(currentNodeIds).stream()
                .collect(Collectors.toMap(FlowNode::getId, node -> node));

        List<FlowListItem> items = flows.stream()
                .map(flow -> toFlowListItem(flow, templateMap, nodeCountMap, currentNodeMap))
                .collect(Collectors.toList());

        Page<FlowListItem> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(items);
        return result;
    }

    @Override
    @Transactional
    public FlowDetail updateFlow(Long id, FlowUpdateRequest request) {
        Flow flow = getById(id);
        if (flow == null) {
            throw new NoSuchElementException("流程不存在: " + id);
        }

        FlowTemplate template = templateService.getById(request.getTemplateId());
        if (template == null) {
            throw new IllegalArgumentException("关联的流程模板不存在: " + request.getTemplateId());
        }

        flow.setFlowName(request.getFlowName());
        flow.setOrderNumber(request.getOrderNumber());
        flow.setTemplateId(request.getTemplateId());
        flow.setTemplateVersion(template.getVersion());
        FlowNode currentNode = resolveCurrentNode(request.getTemplateId(), request.getCurrentNodeId());
        flow.setCurrentNodeId(currentNode == null ? null : currentNode.getId());
        flow.setCurrentNodeState(request.getCurrentNodeState() == null
                ? flow.getCurrentNodeState() : request.getCurrentNodeState());
        flow.setUpdateTime(new Date());
        updateById(flow);

        return toFlowDetail(flow);
    }

    @Override
    @Transactional
    public void deleteFlow(Long id) {
        Flow flow = getById(id);
        if (flow == null) {
            throw new NoSuchElementException("流程不存在: " + id);
        }
        removeById(id);
    }

    @Override
    @Transactional
    public FlowDetail updateProgress(Long id, FlowProgressUpdateRequest request) {
        Flow flow = getById(id);
        if (flow == null) {
            throw new NoSuchElementException("流程不存在: " + id);
        }
        if (request.getEventId() != null && flow.getLastEventId() != null
                && request.getEventId() <= flow.getLastEventId()) {
            return toFlowDetail(flow);
        }
        FlowNode currentNode = resolveCurrentNode(flow.getTemplateId(), request.getCurrentNodeId());
        flow.setCurrentNodeId(currentNode.getId());
        flow.setCurrentNodeState(request.getCurrentNodeState());
        FlowState nextState = request.getFlowState() == null
                ? inferFlowState(flow.getFlowState(), currentNode, request.getCurrentNodeState())
                : request.getFlowState();
        applyFlowState(flow, nextState);
        if (request.getEventId() != null) {
            flow.setLastEventId(request.getEventId());
        }
        if (request.getErrorCode() != null) {
            flow.setErrorCode(request.getErrorCode());
        }
        if (request.getErrorMessage() != null) {
            flow.setErrorMessage(request.getErrorMessage());
        }
        if (request.getAttempt() != null) {
            flow.setAttempt(request.getAttempt());
        }
        if (nextState == FlowState.SUCCEEDED) {
            flow.setErrorCode(null);
            flow.setErrorMessage(null);
        }
        flow.setUpdateTime(new Date());
        if (!updateById(flow)) {
            throw new IllegalStateException("流程已被其他任务更新，请刷新后重试: " + id);
        }
        return toFlowDetail(flow);
    }

    private FlowDetail toFlowDetail(Flow flow) {
        FlowDetail detail = new FlowDetail();
        detail.setId(flow.getId());
        detail.setFlowName(flow.getFlowName());
        detail.setOrderNumber(flow.getOrderNumber());
        detail.setTemplateId(flow.getTemplateId());
        detail.setTemplateVersion(flow.getTemplateVersion());
        detail.setFlowState(flow.getFlowState());
        detail.setCurrentNodeId(flow.getCurrentNodeId());
        detail.setCurrentNodeState(flow.getCurrentNodeState());
        detail.setStartedAt(flow.getStartedAt());
        detail.setCompletedAt(flow.getCompletedAt());
        detail.setVersion(flow.getVersion());
        detail.setLastEventId(flow.getLastEventId());
        detail.setErrorCode(flow.getErrorCode());
        detail.setErrorMessage(flow.getErrorMessage());
        detail.setAttempt(flow.getAttempt());
        detail.setCreateTime(flow.getCreateTime());
        detail.setUpdateTime(flow.getUpdateTime());

        FlowTemplate template = templateService.getById(flow.getTemplateId());
        if (template != null) {
            detail.setTemplateName(template.getTemplateName());

            long nodeCount = nodeService.count(Wrappers.<FlowNode>lambdaQuery()
                    .eq(FlowNode::getTemplateId, template.getId()));
            detail.setNodeCount((int) nodeCount);
        }

        if (flow.getCurrentNodeId() != null) {
            FlowNode currentNode = nodeService.getById(flow.getCurrentNodeId());
            if (currentNode != null) {
                detail.setCurrentNodeName(currentNode.getNodeName());
            }
        }

        return detail;
    }

    private FlowListItem toFlowListItem(Flow flow, Map<Long, FlowTemplate> templateMap,
                                        Map<Long, Long> nodeCountMap,
                                        Map<Long, FlowNode> currentNodeMap) {
        FlowListItem item = new FlowListItem();
        item.setId(flow.getId());
        item.setFlowName(flow.getFlowName());
        item.setOrderNumber(flow.getOrderNumber());
        item.setTemplateVersion(flow.getTemplateVersion());
        item.setFlowState(flow.getFlowState());
        item.setCurrentNodeId(flow.getCurrentNodeId());
        item.setCurrentNodeState(flow.getCurrentNodeState());
        item.setStartedAt(flow.getStartedAt());
        item.setCompletedAt(flow.getCompletedAt());
        item.setErrorCode(flow.getErrorCode());
        item.setErrorMessage(flow.getErrorMessage());
        item.setAttempt(flow.getAttempt());
        item.setUpdateTime(flow.getUpdateTime());

        FlowTemplate template = templateMap.get(flow.getTemplateId());
        if (template != null) {
            item.setTemplateName(template.getTemplateName());
        }

        Long nodeCount = nodeCountMap.get(flow.getTemplateId());
        item.setNodeCount(nodeCount != null ? nodeCount.intValue() : 0);

        FlowNode currentNode = currentNodeMap.get(flow.getCurrentNodeId());
        if (currentNode != null) {
            item.setCurrentNodeName(currentNode.getNodeName());
        }

        return item;
    }

    private FlowNode resolveCurrentNode(Long templateId, Long currentNodeId) {
        FlowNode node;
        if (currentNodeId == null) {
            node = nodeService.getOne(Wrappers.<FlowNode>lambdaQuery()
                    .eq(FlowNode::getTemplateId, templateId)
                    .orderByAsc(FlowNode::getSort)
                    .orderByAsc(FlowNode::getId)
                    .last("limit 1"));
            return node;
        }

        node = nodeService.getById(currentNodeId);
        if (node == null || !templateId.equals(node.getTemplateId())) {
            throw new IllegalArgumentException("当前节点不属于所选流程模板: " + currentNodeId);
        }
        return node;
    }

    private FlowState inferFlowState(FlowState currentState, FlowNode currentNode, NodeState nodeState) {
        if (nodeState == NodeState.FAILED) {
            return FlowState.FAILED;
        }
        if (nodeState == NodeState.CANCELLED) {
            return FlowState.CANCELLED;
        }
        if ((nodeState == NodeState.SUCCEEDED || nodeState == NodeState.SKIPPED)
                && "END".equalsIgnoreCase(currentNode.getNodeCode())) {
            return FlowState.SUCCEEDED;
        }
        if (nodeState == NodeState.RUNNING || nodeState == NodeState.WAITING
                || nodeState == NodeState.SUCCEEDED || nodeState == NodeState.SKIPPED) {
            return FlowState.RUNNING;
        }
        return currentState == null ? FlowState.PENDING : currentState;
    }

    private void applyFlowState(Flow flow, FlowState nextState) {
        LocalDateTime now = LocalDateTime.now();
        flow.setFlowState(nextState);
        if (nextState != FlowState.PENDING && flow.getStartedAt() == null) {
            flow.setStartedAt(now);
        }
        flow.setCompletedAt(nextState.isTerminal() ? now : null);
    }
}
