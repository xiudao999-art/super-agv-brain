package com.kunling.scheduling.workflow.service.impl;

import com.kunling.scheduling.workflow.dto.WorkflowRequests;
import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.service.WorkflowService;
import com.kunling.scheduling.workflow.service.WorkflowStateService;
import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class WorkflowServiceImpl implements WorkflowService {
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final TaskService taskService;
    private final WorkflowStateService workflowStateService;

    public WorkflowServiceImpl(RepositoryService repositoryService, RuntimeService runtimeService,
                               HistoryService historyService, TaskService taskService,
                               WorkflowStateService workflowStateService) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.taskService = taskService;
        this.workflowStateService = workflowStateService;
    }

    @Override
    @Transactional
    public WorkflowResponses.Definition deploy(WorkflowRequests.DeployDefinition request) {
        String resourceName = StringUtils.defaultIfBlank(request.getResourceName(), "process.bpmn20.xml");
        if (!resourceName.endsWith(".bpmn") && !resourceName.endsWith(".bpmn20.xml")) {
            resourceName += ".bpmn20.xml";
        }
        Deployment deployment = repositoryService.createDeployment()
                .name(request.getName()).category(request.getCategory())
                .addString(resourceName, request.getBpmnXml()).deploy();
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId()).singleResult();
        if (definition == null) {
            throw new IllegalStateException("部署成功但未找到流程定义");
        }
        return toDefinition(definition);
    }

    @Override
    public List<WorkflowResponses.Definition> listDefinitions(String key) {
        ProcessDefinitionQuery query = repositoryService
                .createProcessDefinitionQuery().latestVersion().orderByProcessDefinitionKey().asc();
        if (StringUtils.isNotBlank(key)) query.processDefinitionKey(key);
        return query.list().stream().map(this::toDefinition).collect(Collectors.toList());
    }

    @Override
    public String getDefinitionXml(String processDefinitionId) {
        ProcessDefinition definition = requiredDefinition(processDefinitionId);
        try (InputStream input = repositoryService.getResourceAsStream(
                definition.getDeploymentId(), definition.getResourceName())) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) >= 0) output.write(buffer, 0, length);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("读取BPMN XML失败", exception);
        }
    }

    @Override
    @Transactional
    public WorkflowResponses.Instance start(WorkflowRequests.StartInstance request) {
        boolean byId = StringUtils.isNotBlank(request.getProcessDefinitionId());
        boolean byKey = StringUtils.isNotBlank(request.getProcessDefinitionKey());
        if (byId == byKey) {
            throw new IllegalArgumentException("processDefinitionId和processDefinitionKey必须且只能传一个");
        }
        ProcessInstance instance = byId
                ? runtimeService.startProcessInstanceById(request.getProcessDefinitionId(), request.getBusinessKey())
                : runtimeService.startProcessInstanceByKey(request.getProcessDefinitionKey(), request.getBusinessKey());
        return toInstance(instance);


    }

    @Override
    public WorkflowResponses.Instance getInstance(String processInstanceId) {
        return workflowStateService.get(processInstanceId);
    }

    @Override @Transactional
    public WorkflowResponses.Instance suspend(String id) {
        return workflowStateService.suspend(id);
    }

    @Override @Transactional
    public WorkflowResponses.Instance activate(String id) {
        return workflowStateService.activate(id);
    }

    @Override @Transactional
    public WorkflowResponses.Instance terminate(String id, WorkflowRequests.TerminateInstance request) {
        return workflowStateService.terminate(id, request == null ? null : request.getReason());
    }

    @Override
    public List<WorkflowResponses.ActiveNode> listActiveNodes(String id) {
        ProcessInstance instance = requiredActiveInstance(id);
        BpmnModel bpmnModel = repositoryService.getBpmnModel(instance.getProcessDefinitionId());
        return runtimeService.createExecutionQuery().processInstanceId(id).list().stream()
                .filter(item -> StringUtils.isNotBlank(item.getActivityId()))
                .map(item -> new WorkflowResponses.ActiveNode(item.getId(), item.getActivityId(),
                        getActivityName(bpmnModel, item.getActivityId()),
                        item.getProcessInstanceId(),
                        getNodeAttribute(bpmnModel, item.getActivityId(), "actionDefinitionId"),
                        item.isSuspended()))
                .collect(Collectors.toList());
    }

    private String getActivityName(BpmnModel bpmnModel, String activityId) {
        FlowElement flowElement = bpmnModel == null ? null : bpmnModel.getFlowElement(activityId);
        return flowElement == null ? null : flowElement.getName();
    }

    /** 读取模板发布时固化在 BPMN 节点上的业务属性。 */
    private String getNodeAttribute(BpmnModel bpmnModel, String activityId, String attributeName) {
        FlowElement flowElement = bpmnModel == null ? null : bpmnModel.getFlowElement(activityId);
        if (flowElement == null) return null;
        for (Map.Entry<String, List<ExtensionAttribute>> entry : flowElement.getAttributes().entrySet()) {
            for (ExtensionAttribute attribute : entry.getValue()) {
                if (attributeName.equals(entry.getKey()) || attributeName.equals(attribute.getName())) {
                    return StringUtils.trimToNull(attribute.getValue());
                }
            }
        }
        return null;
    }

    @Override @Transactional
    public WorkflowResponses.Instance trigger(WorkflowRequests.TriggerExecution request) {
        Map<String, Object> variables = variables(request.getVariables());
        Object executionId = variables.get("executionId");
        return workflowStateService.completeExecution(
                executionId == null ? null : String.valueOf(executionId), variables);
    }

    @Override
    public List<WorkflowResponses.HistoryNode> listHistory(String id) {
        requiredHistoricInstance(id);
        return historyService.createHistoricActivityInstanceQuery().processInstanceId(id)
                .orderByHistoricActivityInstanceStartTime().asc().list().stream()
                .map(this::toHistoryNode).collect(Collectors.toList());
    }

    @Override
    public List<WorkflowResponses.UserTask> listTasks(String processInstanceId, String assignee) {
        org.flowable.task.api.TaskQuery query = taskService.createTaskQuery().active();
        if (StringUtils.isNotBlank(processInstanceId)) query.processInstanceId(processInstanceId);
        if (StringUtils.isNotBlank(assignee)) query.taskAssignee(assignee);
        return query.orderByTaskCreateTime().asc().list().stream().map(this::toTask).collect(Collectors.toList());
    }

    @Override @Transactional
    public void claimTask(String taskId, WorkflowRequests.ClaimTask request) {
        requiredTask(taskId);
        taskService.claim(taskId, request.getAssignee());
    }

    @Override @Transactional
    public void completeTask(String taskId, WorkflowRequests.CompleteTask request) {
        requiredTask(taskId);
        taskService.complete(taskId, variables(request.getVariables()));
    }

    private ProcessDefinition requiredDefinition(String id) {
        ProcessDefinition value = repositoryService.createProcessDefinitionQuery().processDefinitionId(id).singleResult();
        if (value == null) throw new NoSuchElementException("流程定义不存在: " + id);
        return value;
    }
    private ProcessInstance requiredActiveInstance(String id) {
        ProcessInstance value = runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult();
        if (value == null) throw new NoSuchElementException("运行中的流程实例不存在: " + id);
        return value;
    }
    private HistoricProcessInstance requiredHistoricInstance(String id) {
        HistoricProcessInstance value = historyService.createHistoricProcessInstanceQuery().processInstanceId(id).singleResult();
        if (value == null) throw new NoSuchElementException("流程历史不存在: " + id);
        return value;
    }
    private Task requiredTask(String id) {
        Task value = taskService.createTaskQuery().taskId(id).singleResult();
        if (value == null) throw new NoSuchElementException("人工任务不存在: " + id);
        return value;
    }
    private java.util.Map<String, Object> variables(java.util.Map<String, Object> value) {
        return value == null ? Collections.emptyMap() : value;
    }
    private WorkflowResponses.Definition toDefinition(ProcessDefinition value) {
        return new WorkflowResponses.Definition(value.getId(), value.getKey(), value.getName(), value.getVersion(),
                value.getDeploymentId(), value.getResourceName(), value.getCategory());
    }
    private WorkflowResponses.Instance toInstance(ProcessInstance value) {
        return new WorkflowResponses.Instance(value.getId(), value.getProcessDefinitionId(), value.getBusinessKey(),
                value.isSuspended() ? "SUSPENDED" : "RUNNING", value.isSuspended(), value.getStartTime(), null, null);
    }
    private WorkflowResponses.Instance toInstance(HistoricProcessInstance value) {
        String state = value.getDeleteReason() == null ? "COMPLETED" : "TERMINATED";
        return new WorkflowResponses.Instance(value.getId(), value.getProcessDefinitionId(), value.getBusinessKey(),
                state, false, value.getStartTime(), value.getEndTime(), value.getDeleteReason());
    }
    private WorkflowResponses.HistoryNode toHistoryNode(HistoricActivityInstance value) {
        return new WorkflowResponses.HistoryNode(value.getId(), value.getActivityId(), value.getActivityName(),
                value.getActivityType(), value.getExecutionId(), value.getStartTime(), value.getEndTime(),
                value.getDurationInMillis(), value.getAssignee());
    }
    private WorkflowResponses.UserTask toTask(Task value) {
        return new WorkflowResponses.UserTask(value.getId(), value.getName(), value.getTaskDefinitionKey(),
                value.getAssignee(), value.getProcessInstanceId(), value.getCreateTime());
    }
}
