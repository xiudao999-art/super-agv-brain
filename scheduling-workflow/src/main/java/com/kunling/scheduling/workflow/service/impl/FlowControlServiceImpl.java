package com.kunling.scheduling.workflow.service.impl;

import com.kunling.scheduling.action.commissioning.application.ActionParameterSetService;
import com.kunling.scheduling.action.commissioning.application.ActionParameterSetView;
import com.kunling.scheduling.action.execution.application.ActionExecutionReceipt;
import com.kunling.scheduling.action.execution.application.ActionExecutionService;
import com.kunling.scheduling.action.execution.application.ExecuteActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.agvflow.domain.dto.FlowCreateRequest;

import com.kunling.scheduling.agvflow.enums.NodeState;
import com.kunling.scheduling.workflow.dto.WorkflowRequests;
import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.entity.Flow;
import com.kunling.scheduling.workflow.enums.NodeStateEnum;
import com.kunling.scheduling.workflow.service.FlowControlService;
import com.kunling.scheduling.workflow.service.FlowService;
import com.kunling.scheduling.workflow.service.WorkflowService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.transaction.Transactional;
import java.util.List;
import java.util.NoSuchElementException;

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
    public boolean start(String processDefinitionId,Long businessKey,Long template){
        if (StringUtils.isBlank(processDefinitionId) || businessKey == null){
            throw new IllegalArgumentException("参数异常");
        }
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
        if (StringUtils.isBlank(actionKey) || StringUtils.isBlank(executionId)){
            return failAndClear(id, "活动节点ID或执行ID为空");
        }
        //查询机器人
        List<RobotSessionView> robotSessionViews = robotActionTransport.listSessions();
        if (CollectionUtils.isEmpty(robotSessionViews)){
            return failAndClear(id, "未找到在线机器人");
        }
        String robotId = robotSessionViews.get(0).robotId();

        //调用小邓的接口
        List<ActionParameterSetView> actionParameterSetViews = parameterSetService.list(actionKey);
        if (CollectionUtils.isEmpty(actionParameterSetViews)){
            return failAndClear(id, "节点未配置动作参数: " + actionKey);
        }
        ActionParameterSetView actionParameterSetView = actionParameterSetViews.get(0);
        String actionInstanceId = actionParameterSetView.id();

        //创建流程数据
        Flow flow = new Flow();
        flow.setTaskId(businessKey);
        flow.setOrderNumber("123456");
        flow.setTemplateId(template);
        flow.setCurrentNodeState(NodeStateEnum.PENDING);
        flow.setCurrentNode(activeNode.getActivityId());
        flowService.save(flow);
        ExecuteActionCommand request = new ExecuteActionCommand(String.valueOf(businessKey), executionId, String.valueOf(flow.getId()), robotId, actionKey, actionInstanceId);
        actionExecutionService.execute(request);
        //调用
        return true;
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
