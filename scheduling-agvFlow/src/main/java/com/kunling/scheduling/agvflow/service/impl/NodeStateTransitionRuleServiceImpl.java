package com.kunling.scheduling.agvflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.kunling.scheduling.agvflow.domain.dto.StatusChangedDto;
import com.kunling.scheduling.agvflow.domain.entity.FlowNode;
import com.kunling.scheduling.agvflow.domain.entity.FlowTemplate;
import com.kunling.scheduling.agvflow.domain.entity.NodeStateTransitionRule;
import com.kunling.scheduling.agvflow.enums.NodeState;
import com.kunling.scheduling.agvflow.mapper.NodeStateTransitionRuleMapper;
import com.kunling.scheduling.agvflow.service.FlowNodeService;
import com.kunling.scheduling.agvflow.service.FlowTemplateService;
import com.kunling.scheduling.agvflow.service.NodeStateTransitionRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
@Slf4j
public class NodeStateTransitionRuleServiceImpl
        extends ServiceImpl<NodeStateTransitionRuleMapper, NodeStateTransitionRule>
        implements NodeStateTransitionRuleService {

    @Resource
    private FlowNodeService flowNodeService;

    @Resource
    private FlowTemplateService flowTemplateService;

    private static final int FLOW_SUCCEEDED = 2;
    private static final int FLOW_FAILED = 3;

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

        Integer nodeId;
        try {
            nodeId = Integer.valueOf(dto.getWorkflowNodeInstanceId());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("workflowNodeInstanceId 必须是整数", exception);
        }

        FlowNode flowNode = flowNodeService.getById(nodeId);
        if (flowNode == null) {
            throw new IllegalArgumentException("流程节点不存在: " + nodeId);
        }

        String eventCode = dto.getEventCode().trim().toUpperCase(java.util.Locale.ROOT);
        log.info("处理节点状态回调: nodeId={}, currentState={}, eventCode={}",
                flowNode.getId(), flowNode.getStatus(), eventCode);

        switch (eventCode) {
            case "SUCCEEDED":
                handleSucceeded(flowNode);
                break;
            case "RETRYABLE":
                handleRetryable(flowNode);
                break;
            case "MANUAL_INTERVENTION":
                // 维持当前节点状态，等待人工接口再次触发。
                log.warn("节点需要人工介入，保持当前状态: nodeId={}, state={}",
                        flowNode.getId(), flowNode.getStatus());
                break;
            case "NON_RETRYABLE":
                updateNodeState(flowNode, NodeState.FAILED);
                log.warn("节点发生不可重试失败，等待外部处理: nodeId={}", flowNode.getId());
                break;
            case "CRITICAL":
                handleCritical(flowNode);
                break;
            default:
                throw new IllegalArgumentException("不支持的节点事件状态: " + eventCode);
        }
    }

    private void handleSucceeded(FlowNode currentNode) {
        // 终态回调可能被重复投递，已成功的节点不能再次启动下一节点。
        if (currentNode.getStatus() == NodeState.SUCCEEDED) {
            log.info("忽略重复的节点成功回调: nodeId={}", currentNode.getId());
            return;
        }
        updateNodeState(currentNode, NodeState.SUCCEEDED);

        FlowNode nextNode = flowNodeService.getOne(Wrappers.<FlowNode>lambdaQuery()
                .eq(FlowNode::getTemplateId, currentNode.getTemplateId())
                .gt(FlowNode::getSort, currentNode.getSort())
                .eq(FlowNode::getStatus, NodeState.PENDING)
                .orderByAsc(FlowNode::getSort)
                .last("limit 1"), false);

        if (nextNode == null) {
            updateFlowStatus(currentNode.getTemplateId(), FLOW_SUCCEEDED);
            log.info("流程全部节点执行完成: flowId={}", currentNode.getTemplateId());
            return;
        }
        flowTemplateService.startFlowNode(nextNode.getId());
    }

    private void handleRetryable(FlowNode currentNode) {
        if (currentNode.getStatus() != NodeState.RUNNING
                && currentNode.getStatus() != NodeState.WAITING) {
            throw new IllegalStateException("当前节点状态不允许重试: " + currentNode.getStatus());
        }
        updateNodeState(currentNode, NodeState.WAITING);
        flowTemplateService.startFlowNode(currentNode.getId());
    }

    private void handleCritical(FlowNode currentNode) {
        updateNodeState(currentNode, NodeState.FAILED);
        updateFlowStatus(currentNode.getTemplateId(), FLOW_FAILED);

        // 严重错误终止流程，尚未启动的节点统一取消。
        List<FlowNode> pendingNodes = flowNodeService.list(Wrappers.<FlowNode>lambdaQuery()
                .eq(FlowNode::getTemplateId, currentNode.getTemplateId())
                .eq(FlowNode::getStatus, NodeState.PENDING));
        for (FlowNode pendingNode : pendingNodes) {
            pendingNode.setStatus(NodeState.CANCELLED);
        }
        if (!pendingNodes.isEmpty()) {
            flowNodeService.updateBatchById(pendingNodes);
        }
        log.error("流程因严重错误终止: flowId={}, nodeId={}",
                currentNode.getTemplateId(), currentNode.getId());
    }

    private void updateNodeState(FlowNode node, NodeState state) {
        node.setStatus(state);
        if (!flowNodeService.updateById(node)) {
            throw new IllegalStateException("流程节点状态更新失败: " + node.getId());
        }
    }

    private void updateFlowStatus(Long flowId, int status) {
        FlowTemplate flow = flowTemplateService.getById(flowId);
        if (flow == null) {
            throw new IllegalStateException("流程不存在: " + flowId);
        }
        flow.setStatus(status);
        if (!flowTemplateService.updateById(flow)) {
            throw new IllegalStateException("流程状态更新失败: " + flowId);
        }
    }
}
