package com.kunling.scheduling.workflow.service.impl;

import com.kunling.scheduling.action.commissioning.application.ActionParameterSetService;
import com.kunling.scheduling.action.commissioning.application.ActionParameterSetView;
import com.kunling.scheduling.action.execution.application.ActionExecutionService;
import com.kunling.scheduling.action.execution.application.ExecuteActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.workflow.dto.*;
import com.kunling.scheduling.workflow.entity.FlowNode;
import com.kunling.scheduling.workflow.entity.FlowTemplate;
import com.kunling.scheduling.workflow.entity.WorkflowTemplateEntity;
import com.kunling.scheduling.workflow.enums.NodeState;
import com.kunling.scheduling.workflow.enums.NodeStateEnum;
import com.kunling.scheduling.workflow.enums.StartTypeEnum;
import com.kunling.scheduling.workflow.mapper.FlowTemplateMapper;
import com.kunling.scheduling.workflow.mapper.WorkflowTemplateMapper;
import com.kunling.scheduling.workflow.order.application.TaskFlowStatusEvent;
import com.kunling.scheduling.workflow.order.domain.OrderTask;
import com.kunling.scheduling.workflow.order.domain.OrderTaskStatus;
import com.kunling.scheduling.workflow.order.infrastructure.OrderTaskMapper;
import com.kunling.scheduling.workflow.service.*;
import com.kunling.scheduling.workflow.service.WorkflowStateService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

@Service
public class FlowControlServiceImpl implements FlowControlService {

    private static final Logger log = LoggerFactory.getLogger(FlowControlServiceImpl.class);

    @Resource
    private WorkflowService workflowService;
    @Resource
    private RobotActionTransport robotActionTransport;
    @Resource
    private ActionParameterSetService parameterSetService;
    @Resource
    private OrderTaskMapper orderTaskMapper;
    @Resource
    private ActionExecutionService actionExecutionService;
    @Resource
    private WorkflowStateService workflowStateService;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    private FlowNodeService flowNodeService;
    @Resource
    private WorkflowTemplateMapper workflowTemplateMapper;

    @Resource
    private FlowTemplateMapper flowTemplateMapper;

    @Resource
    private WorkflowTemplateService workflowTemplateService;


    @Override
    @Transactional
    public boolean start(FlowStartRequest request) {
        if (request == null || StringUtils.isBlank(request.getProcessDefinitionId())
                || request.getBusinessKey() == null || request.getTemplateId() == null
                || request.getTaskId() == null) {
            throw new IllegalArgumentException("参数异常");
        }
        OrderTask task = orderTaskMapper.selectById(request.getTaskId());
        if (task == null) throw new NoSuchElementException("订单任务不存在: " + request.getTaskId());
        task.setFlowTemplateId(request.getTemplateId());
        task.setProcessDefinitionId(request.getProcessDefinitionId());
        task.setStatus(OrderTaskStatus.RUNNING);
        task.setStartedAt(LocalDateTime.now());
        task.setCompletedAt(null);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setAttempt(task.getAttempt() == null ? 0 : task.getAttempt());
        //机器人不存在
        List<RobotSessionView> robotSessions = robotActionTransport.listSessions();
        if (CollectionUtils.isEmpty(robotSessions)) {
            throw new NoSuchElementException("当前没有可用的机器人");
        }

        String processDefinitionId = request.getProcessDefinitionId();
        Long businessKey = request.getBusinessKey();
        ;
        WorkflowRequests.StartInstance startInstance = new WorkflowRequests.StartInstance();
        startInstance.setBusinessKey(String.valueOf(businessKey));
        startInstance.setProcessDefinitionId(processDefinitionId);
        //运行当前节点
        WorkflowResponses.Instance start = workflowService.start(startInstance);

        String id = start.getId();
        //查询运行的当前节点
        List<WorkflowResponses.ActiveNode> activeNodes;
        try {
            activeNodes = workflowService.listActiveNodes(id);
        } catch (NoSuchElementException exception) {
            // 流程可能已经自动结束，此时ACT_RU_EXECUTION本身已经没有数据。
            task.setStatus(OrderTaskStatus.FAILED);
            task.setErrorMessage("启动后未找到运行中的流程实例");
            orderTaskMapper.updateById(task);
            log.warn("启动后未找到运行中的流程实例，processInstanceId={}", id, exception);
            return false;
        }
        if (CollectionUtils.isEmpty(activeNodes)) {
            return failAndClear(id, "启动后未找到活动节点");
        }
        WorkflowResponses.ActiveNode activeNode = activeNodes.get(0);
        String processInstanceId = activeNode.getProcessInstanceId();
        task.setProcessInstanceId(processInstanceId);
        orderTaskMapper.updateById(task);
        return dispatchDownstreamAction(id, task.getId(), activeNode, StartTypeEnum.START);
    }

//
//    @Override
//    @Transactional
//    public boolean processCallback(FlowStartRequest request) {
//        if (StringUtils.isBlank(request.getExecutionId()) || request.getFlowId() == null
//                || request.getBusinessKey() == null) {
//            throw new IllegalArgumentException("executionId、flowId和businessKey不能为空");
//        }
//
//        final long flowId;
//        final Long businessId;
//        try {
//            flowId = request.getFlowId();
//            businessId = request.getBusinessKey();
//        } catch (NumberFormatException exception) {
//            throw new IllegalArgumentException("flowId和businessKey必须是整数", exception);
//        }
//
//        Flow flow = flowService.getById(flowId);
//        if (flow == null) {
//            throw new NoSuchElementException("流程业务记录不存在: " + flowId);
//        }
//        if (!businessId.equals(flow.getTaskId())) {
//            throw new IllegalArgumentException("回调businessKey与流程业务记录不一致");
//        }
//        // Action终态报告可能重复投递；已成功的流程直接按成功处理，避免重复trigger。
//        if (flow.getFlowState() == FlowState.SUCCEEDED) {
//            return true;
//        }
//
//        //完成当前节点
//        WorkflowRequests.TriggerExecution trigger = new WorkflowRequests.TriggerExecution();
//        Map<String, Object> variables = new HashMap<>();
//        variables.put("executionId", request.getExecutionId());
//        variables.put("success", true);
//        variables.put("nodeState", NodeStateEnum.SUCCEEDED.name());
//        variables.put("deviceStatus", "COMPLETED");
//        trigger.setVariables(variables);
//
//        WorkflowResponses.Instance instance = workflowService.trigger(trigger);
//        flow.setCurrentNodeState(NodeStateEnum.SUCCEEDED);
//
//        if ("COMPLETED".equals(instance.getState())) {
//            settleSucceeded(flow);
//        } else {
//            List<WorkflowResponses.ActiveNode> activeNodes = workflowService.listActiveNodes(instance.getId());
//            if (activeNodes.isEmpty()) {
//                // 当前业务流程是串行流程；成功推进后没有下一活动节点即表示已经走到流程末尾。
//                settleSucceeded(flow);
//            } else {
//                WorkflowResponses.ActiveNode next = activeNodes.get(0);
//                flow.setFlowState(FlowState.RUNNING);
//                flow.setCurrentNode(next.getActivityId());
//                flow.setCurrentNodeState(NodeStateEnum.PENDING);
//
//                if (!dispatchDownstreamAction(instance.getId(), flowId,
//                        next, StartTypeEnum.CALLBACK)) {
//                    eventPublisher.publishEvent(new TaskFlowStatusEvent(flow.getTaskId(),
//                            TaskFlowStatusEvent.Type.WAITING, instance.getId(), next.getActivityId(),
//                            "下一个流程节点等待人工恢复"));
//                    return false;
//                }
//            }
//        }
//        boolean updated = flowService.updateById(flow);
//        if (updated && flow.getFlowState() == FlowState.SUCCEEDED) {
//            eventPublisher.publishEvent(new TaskFlowStatusEvent(flow.getTaskId(),
//                    TaskFlowStatusEvent.Type.SUCCEEDED, flow.getProcessInstanceId(),
//                    flow.getCurrentNode(), null));
//        }
//        return updated;
//    }

    /**
     * 选择在线机器人和节点参数集，并将当前Flowable节点下发给Action系统。
     */
    public boolean dispatchDownstreamAction(String processInstanceId,
                                            Long taskId,
                                            WorkflowResponses.ActiveNode activeNode,
                                            StartTypeEnum startType) {
        if (activeNode == null || StringUtils.isBlank(activeNode.getActivityId())
                || StringUtils.isBlank(activeNode.getExecutionId())) {
            return failAndClear(processInstanceId, "下游调度节点ID或执行ID为空");
        }

        List<RobotSessionView> robotSessions = robotActionTransport.listSessions();
        if (CollectionUtils.isEmpty(robotSessions)) {
            if (startType == StartTypeEnum.START) {
                return failAndClear(processInstanceId, "未找到在线机器人");
            } else {
                return suspendAndWait(processInstanceId, taskId, "未找到在线机器人，等待人工恢复");
            }
        }
        String robotId = robotSessions.get(0).robotId();

        String actionKey = activeNode.getActivityId();
        List<ActionParameterSetView> parameterSets = parameterSetService.list(actionKey);
        if (CollectionUtils.isEmpty(parameterSets)) {
            if (startType == StartTypeEnum.START) {
                return failAndClear(processInstanceId, "节点未配置动作参数: " + actionKey);
            } else {
                return suspendAndWait(processInstanceId, taskId, "节点未配置动作参数: " + actionKey);
            }
        }
        String parameterSetId = parameterSets.get(0).id();
        //处理业务node表
        List<WorkflowResponses.ActiveNode> activeNodes = workflowService.listActiveNodes(processInstanceId);
        int count = flowNodeService.lambdaQuery().eq(FlowNode::getTaskId, taskId).count().intValue();
        FlowNode flowNode = new FlowNode();
        flowNode.setNodeName(activeNodes.get(0).getActivityName());
        flowNode.setTaskId(taskId);
        flowNode.setProcessInstanceId(processInstanceId);
        flowNode.setSort(count + 1);
        flowNode.setStatus(NodeState.RUNNING);
        flowNode.setNodeCode(activeNodes.get(0).getActivityId());
        flowNodeService.save(flowNode);
        OrderTask task = orderTaskMapper.selectById(taskId);
        if (task == null) {
            throw new NoSuchElementException("订单任务不存在: " + taskId);
        }
        task.setProcessInstanceId(processInstanceId);
        task.setStatus(OrderTaskStatus.RUNNING);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        orderTaskMapper.updateById(task);
        log.info("流程节点----{}--开始进行", activeNodes.get(0).getActivityId());
        ExecuteActionCommand command = new ExecuteActionCommand(
                taskId.toString(),
                flowNode.getId().toString(),
                UUID.randomUUID().toString(),
                robotId,
                actionKey,
                parameterSetId);
        actionExecutionService.execute(command);
        return true;
    }

    /**
     * 保留当前流程运行数据，将Flowable实例挂起，并把业务节点标记为等待中。
     * 后续恢复时应先激活processInstanceId，再重新下发当前活动节点。
     */
    private boolean suspendAndWait(String processInstanceId, Long taskId, String reason) {
        workflowStateService.suspend(processInstanceId);

        OrderTask task = orderTaskMapper.selectById(taskId);
        if (task == null) {
            throw new NoSuchElementException("订单任务不存在: " + taskId);
        }
        task.setStatus(OrderTaskStatus.QUEUED);
        task.setErrorCode("ROBOT_OFFLINE");
        task.setErrorMessage(reason);
        if (orderTaskMapper.updateById(task) != 1) {
            throw new IllegalStateException("任务等待状态保存失败: " + taskId);
        }

        log.warn("流程因无在线机器人已挂起，processInstanceId={}, taskId={}", processInstanceId, taskId);
        return false;
    }

//    @Override
//    @Transactional
//    public boolean processFailure(FlowFailureCallbackRequest request) {
//        if (request == null) throw new IllegalArgumentException("失败回调不能为空");
//        final Long flowId;
//        final Long taskId;
//        try {
//            flowId = Long.valueOf(request.getFlowId());
//            taskId = Long.valueOf(request.getBusinessKey());
//        } catch (NumberFormatException exception) {
//            throw new IllegalArgumentException("flowId和businessKey必须是整数", exception);
//        }
//        Flow flow = flowService.getById(flowId);
//        if (flow == null || !taskId.equals(flow.getTaskId())) {
//            throw new IllegalArgumentException("失败回调与流程业务记录不匹配");
//        }
//        if (flow.getFlowState() == FlowState.FAILED) return true;
//        if (StringUtils.isNotBlank(flow.getProcessInstanceId())) {
//            clear(flow.getProcessInstanceId(), request.getErrorMessage());
//        }
//        flow.setFlowState(FlowState.FAILED);
//        flow.setCurrentNodeState(NodeStateEnum.FAILED);
//        flow.setCompletedAt(LocalDateTime.now());
//        flow.setErrorCode(StringUtils.defaultIfBlank(request.getErrorCode(), "ACTION_FAILED"));
//        flow.setErrorMessage(request.getErrorMessage());
//        boolean updated = flowService.updateById(flow);
//        if (updated) {
//            eventPublisher.publishEvent(new TaskFlowStatusEvent(taskId, TaskFlowStatusEvent.Type.FAILED,
//                    flow.getProcessInstanceId(), flow.getCurrentNode(), request.getErrorMessage()));
//        }
//        return updated;
//    }

    @Override
    @Transactional
    public boolean resumeTask(Long taskId) {
        if (taskId == null) throw new IllegalArgumentException("taskId不能为空");
        OrderTask task = orderTaskMapper.selectById(taskId);
        if (task == null || StringUtils.isBlank(task.getProcessInstanceId())) {
            throw new NoSuchElementException("任务没有可恢复的流程实例: " + taskId);
        }
        workflowStateService.activate(task.getProcessInstanceId());
        List<WorkflowResponses.ActiveNode> activeNodes = workflowService.listActiveNodes(task.getProcessInstanceId());
        if (activeNodes.isEmpty()) throw new NoSuchElementException("恢复后没有活动节点: " + taskId);
        WorkflowResponses.ActiveNode activeNode = activeNodes.get(0);
        boolean dispatched = dispatchDownstreamAction(task.getProcessInstanceId(),
                task.getId(), activeNode, StartTypeEnum.CALLBACK);
        if (dispatched) {
            task.setStatus(OrderTaskStatus.RUNNING);
            task.setErrorCode(null);
            task.setErrorMessage(null);
            orderTaskMapper.updateById(task);
        }
        return dispatched;
    }


    /**
     * 清理单个异常流程实例。不要直接DELETE ACT_RU_EXECUTION，Flowable会同时清理
     * 该流程在ACT_RU_TASK、ACT_RU_VARIABLE等运行表中的关联数据。
     */
    @Override
    @Transactional
    public boolean clear(String processInstanceId, String reason) {
        if (StringUtils.isBlank(processInstanceId)) {
            throw new IllegalArgumentException("processInstanceId不能为空");
        }
        WorkflowRequests.TerminateInstance request = new WorkflowRequests.TerminateInstance();
        request.setReason(StringUtils.defaultIfBlank(reason, "流程启动或节点数据异常，自动清理"));
        try {
            workflowService.terminate(processInstanceId, request);
            log.info("已清理异常流程实例，processInstanceId={}, reason={}", processInstanceId, request.getReason());
            return true;
        } catch (NoSuchElementException exception) {
            // 已正常结束或已被其他线程终止时，运行表中已经没有需要清理的数据。
            log.info("流程实例已不在运行表中，无需重复清理，processInstanceId={}", processInstanceId);
            return false;
        }
    }

    private boolean failAndClear(String processInstanceId, String reason) {
        clear(processInstanceId, reason);
        return false;
    }
}
