package com.kunling.scheduling.workflow.service;

import com.kunling.scheduling.workflow.dto.WorkflowRequests;
import com.kunling.scheduling.workflow.dto.WorkflowResponses;

import java.util.List;

public interface WorkflowService {
    WorkflowResponses.Definition deploy(WorkflowRequests.DeployDefinition request);
    List<WorkflowResponses.Definition> listDefinitions(String key);
    String getDefinitionXml(String processDefinitionId);
    //启动
    WorkflowResponses.Instance start(WorkflowRequests.StartInstance request);
    WorkflowResponses.Instance getInstance(String processInstanceId);
    WorkflowResponses.Instance suspend(String processInstanceId);
    WorkflowResponses.Instance activate(String processInstanceId);
    WorkflowResponses.Instance terminate(String processInstanceId, WorkflowRequests.TerminateInstance request);
    //查询当前节点
    List<WorkflowResponses.ActiveNode> listActiveNodes(String processInstanceId);
    /** 当前活动节点是否直接连接受控异常判断网关。 */
    boolean hasExceptionGatewayAfter(String processInstanceId, String activityId);
    WorkflowResponses.Instance trigger(WorkflowRequests.TriggerExecution request);
    List<WorkflowResponses.HistoryNode> listHistory(String processInstanceId);
    List<WorkflowResponses.UserTask> listTasks(String processInstanceId, String assignee);
    void claimTask(String taskId, WorkflowRequests.ClaimTask request);
    void completeTask(String taskId, WorkflowRequests.CompleteTask request);
}
