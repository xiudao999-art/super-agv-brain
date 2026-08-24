package com.kunling.scheduling.workflow.service.impl;

import com.kunling.scheduling.action.commissioning.application.ActionParameterSetService;
import com.kunling.scheduling.action.commissioning.application.ActionParameterSetView;
import com.kunling.scheduling.action.execution.application.ActionExecutionService;
import com.kunling.scheduling.action.execution.application.ExecuteActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.workflow.dto.WorkflowRequests;
import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.dto.FlowStartRequest;
import com.kunling.scheduling.workflow.entity.Flow;
import com.kunling.scheduling.workflow.enums.FlowState;
import com.kunling.scheduling.workflow.enums.NodeStateEnum;
import com.kunling.scheduling.workflow.enums.StartTypeEnum;
import com.kunling.scheduling.workflow.service.FlowControlService;
import com.kunling.scheduling.workflow.service.FlowService;
import com.kunling.scheduling.workflow.service.WorkflowService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static com.baomidou.mybatisplus.extension.toolkit.Db.updateById;

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
    private FlowService flowService;
    @Resource
    private ActionExecutionService actionExecutionService;


    @Override
    @Transactional
    public boolean start(FlowStartRequest request){
        if (request == null || StringUtils.isBlank(request.getProcessDefinitionId())
                || request.getBusinessKey() == null || request.getTemplateId() == null){
            throw new IllegalArgumentException("参数异常");
        }
        String processDefinitionId = request.getProcessDefinitionId();
        Long businessKey = request.getBusinessKey();;
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
            log.warn("启动后未找到运行中的流程实例，processInstanceId={}", id, exception);
            return false;
        }
        if (CollectionUtils.isEmpty(activeNodes)){
            return failAndClear(id, "启动后未找到活动节点");
        }
        WorkflowResponses.ActiveNode activeNode = activeNodes.get(0);
        String executionId = activeNode.getExecutionId();
        String actionKey = activeNode.getActivityId();
        String processInstanceId = activeNode.getProcessInstanceId();
        if (StringUtils.isBlank(actionKey) || StringUtils.isBlank(executionId)){
            return failAndClear(id, "活动节点ID或执行ID为空");
        }

        //创建流程数据
        Flow flow = new Flow();
        flow.setTaskId(request.getTaskId());
        flow.setOrderNumber(String.valueOf(request.getBusinessKey()));
        flow.setTemplateId(request.getTemplateId());
        flow.setCurrentNode(activeNode.getActivityId());
        flow.setProcessInstanceId(processInstanceId);
        flowService.save(flow);

        return dispatchDownstreamAction(id, String.valueOf(businessKey), flow.getId(), activeNode, StartTypeEnum.START);
    }


    @Override
    @Transactional
    public boolean processCallback(FlowStartRequest request) {
        if (StringUtils.isBlank(request.getExecutionId()) || request.getFlowId() == null
                || request.getBusinessKey() == null) {
            throw new IllegalArgumentException("executionId、flowId和businessKey不能为空");
        }

        final long flowId;
        final Long businessId;
        try {
            flowId = request.getFlowId();
            businessId = request.getBusinessKey();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("flowId和businessKey必须是整数", exception);
        }

        Flow flow = flowService.getById(flowId);
        if (flow == null) {
            throw new NoSuchElementException("流程业务记录不存在: " + flowId);
        }
        if (!businessId.equals(flow.getTaskId())) {
            throw new IllegalArgumentException("回调businessKey与流程业务记录不一致");
        }
        // Action终态报告可能重复投递；已成功的流程直接按成功处理，避免重复trigger。
        if (flow.getFlowState() == FlowState.SUCCEEDED) {
            return true;
        }

        //完成当前节点
        WorkflowRequests.TriggerExecution trigger = new WorkflowRequests.TriggerExecution();
        Map<String, Object> variables = new HashMap<>();
        variables.put("executionId", request.getExecutionId());
        variables.put("success", true);
        variables.put("nodeState", NodeStateEnum.SUCCEEDED.name());
        variables.put("deviceStatus", "COMPLETED");
        trigger.setVariables(variables);

        WorkflowResponses.Instance instance = workflowService.trigger(trigger);
        flow.setCurrentNodeState(NodeStateEnum.SUCCEEDED);

        if ("COMPLETED".equals(instance.getState())) {
            settleSucceeded(flow);
        } else {
            List<WorkflowResponses.ActiveNode> activeNodes = workflowService.listActiveNodes(instance.getId());
            if (activeNodes.isEmpty()) {
                // 当前业务流程是串行流程；成功推进后没有下一活动节点即表示已经走到流程末尾。
                settleSucceeded(flow);
            } else {
                WorkflowResponses.ActiveNode next = activeNodes.get(0);
                flow.setFlowState(FlowState.RUNNING);
                flow.setCurrentNode(next.getActivityId());
                flow.setCurrentNodeState(NodeStateEnum.PENDING);

                if (!dispatchDownstreamAction(instance.getId(), String.valueOf(businessId), flowId,
                        next, StartTypeEnum.CALLBACK)) {
                    return false;
                }
            }
        }
        return updateById(flow);
    }

    /** 选择在线机器人和节点参数集，并将当前Flowable节点下发给Action系统。 */
    private boolean dispatchDownstreamAction(String processInstanceId,
                                             String businessKey,
                                             Long flowId,
                                             WorkflowResponses.ActiveNode activeNode,
                                             StartTypeEnum startType) {
        if (activeNode == null || StringUtils.isBlank(activeNode.getActivityId())
                || StringUtils.isBlank(activeNode.getExecutionId())) {
            return failAndClear(processInstanceId, "下游调度节点ID或执行ID为空");
        }

        List<RobotSessionView> robotSessions = robotActionTransport.listSessions();
        if (CollectionUtils.isEmpty(robotSessions)) {
            if (startType == StartTypeEnum.START){
                return failAndClear(processInstanceId, "未找到在线机器人");
            }else {

            }

        }
        String robotId = robotSessions.get(0).robotId();

        String actionKey = activeNode.getActivityId();
        List<ActionParameterSetView> parameterSets = parameterSetService.list(actionKey);
        if (CollectionUtils.isEmpty(parameterSets)) {
            return failAndClear(processInstanceId, "节点未配置动作参数: " + actionKey);
        }
        String parameterSetId = parameterSets.get(0).id();

        ExecuteActionCommand command = new ExecuteActionCommand(
                businessKey,
                activeNode.getExecutionId(),
                String.valueOf(flowId),
                robotId,
                actionKey,
                parameterSetId);
        actionExecutionService.execute(command);
        return true;
    }

    private void settleSucceeded(Flow flow) {
        flow.setFlowState(FlowState.SUCCEEDED);
        flow.setCurrentNodeState(NodeStateEnum.SUCCEEDED);
        flow.setCompletedAt(LocalDateTime.now());
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
