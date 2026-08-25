package com.kunling.scheduling.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.kunling.scheduling.workflow.dto.StatusChangedDto;
import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.entity.Flow;
import com.kunling.scheduling.workflow.entity.FlowNode;
import com.kunling.scheduling.workflow.entity.NodeStateTransitionRule;
import com.kunling.scheduling.workflow.enums.FlowState;
import com.kunling.scheduling.workflow.enums.NodeState;
import com.kunling.scheduling.workflow.enums.NodeStateEnum;
import com.kunling.scheduling.workflow.enums.StartTypeEnum;
import com.kunling.scheduling.workflow.mapper.NodeStateTransitionRuleMapper;
import com.kunling.scheduling.workflow.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Flow flow = new Flow();
        flow.setId(Long.valueOf(dto.getWorkflowInstanceId()));
        switch (eventCode) {
            case "SUCCEEDED":
                flow.setFlowState(FlowState.SUCCEEDED);
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
                flow.setFlowState(FlowState.FAILED);
                handleCritical(flowNode);
                break;
            default:
                throw new IllegalArgumentException("不支持的节点事件状态: " + eventCode);
        }
        updateNodeState(flowNode, rule.getNextState());
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
            log.info("当前流程flow{}全部完成", currentNode.getTemplateId());
        } else {
            //未完成的话,执行下发下一节点
            flowControlService.dispatchDownstreamAction(currentNode.getProcessInstanceId(), currentNode.getTemplateId(), activeNode, StartTypeEnum.START);
        }

    }


    private void handleRetryable(FlowNode currentNode, WorkflowResponses.ActiveNode activeNode) {
        if (currentNode.getStatus() != NodeState.RUNNING
                && currentNode.getStatus() != NodeState.WAITING) {
            throw new IllegalStateException("当前节点状态不允许重试: " + currentNode.getStatus());
        }
        flowControlService.dispatchDownstreamAction(currentNode.getProcessInstanceId(), currentNode.getTemplateId(), activeNode, StartTypeEnum.START);

    }

    //将当前节点任务挂起
    private void handleManual(FlowNode currentNode) {
        WorkflowResponses.Instance suspend = workflowStateService.suspend(currentNode.getProcessInstanceId());
    }

    private void handleCritical(FlowNode currentNode) {
        // 严重错误终止流程，尚未启动的节点统一取消。
        workflowStateService.terminate(currentNode.getProcessInstanceId(), "严重错误终止流程，尚未启动的节点统一取消");
        List<FlowNode> pendingNodes = flowNodeService.list(Wrappers.<FlowNode>lambdaQuery()
                .eq(FlowNode::getTemplateId, currentNode.getTemplateId()));
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

}
