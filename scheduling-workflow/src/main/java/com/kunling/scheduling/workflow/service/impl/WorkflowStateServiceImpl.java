package com.kunling.scheduling.workflow.service.impl;

import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.enums.ProcessState;
import com.kunling.scheduling.workflow.service.WorkflowStateService;
import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class WorkflowStateServiceImpl implements WorkflowStateService {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    public WorkflowStateServiceImpl(RuntimeService runtimeService, HistoryService historyService) {
        this.runtimeService = runtimeService;
        this.historyService = historyService;
    }

    @Override
    public WorkflowResponses.Instance get(String processInstanceId) {
        requireId(processInstanceId, "processInstanceId");
        ProcessInstance runtime = findRuntime(processInstanceId);
        if (runtime != null) return toResponse(runtime);

        HistoricProcessInstance historic = findHistoric(processInstanceId);
        if (historic == null) {
            throw new NoSuchElementException("流程实例不存在: " + processInstanceId);
        }
        return toResponse(historic);
    }

    @Override
    public ProcessState getState(String processInstanceId) {
        return ProcessState.valueOf(get(processInstanceId).getState());
    }

    @Override
    @Transactional
    public WorkflowResponses.Instance suspend(String processInstanceId) {
        ProcessInstance instance = requireRuntime(processInstanceId);
        if (!instance.isSuspended()) {
            runtimeService.suspendProcessInstanceById(processInstanceId);
        }
        return get(processInstanceId);
    }

    @Override
    @Transactional
    public WorkflowResponses.Instance activate(String processInstanceId) {
        ProcessInstance instance = requireRuntime(processInstanceId);
        if (instance.isSuspended()) {
            runtimeService.activateProcessInstanceById(processInstanceId);
        }
        return get(processInstanceId);
    }


    @Override
    @Transactional
    public WorkflowResponses.Instance completeExecution(String executionId, Map<String, Object> variables) {
        requireId(executionId, "executionId");
        Execution execution = runtimeService.createExecutionQuery().executionId(executionId).singleResult();
        if (execution == null || StringUtils.isBlank(execution.getActivityId())) {
            throw new NoSuchElementException("活动执行节点不存在: " + executionId);
        }
        if (execution.isSuspended()) {
            throw new IllegalStateException("流程已挂起，不能推进节点: " + executionId);
        }

        String processInstanceId = execution.getProcessInstanceId();
        runtimeService.trigger(executionId, safeVariables(variables));
        return get(processInstanceId);
    }

    @Override
    @Transactional
    public WorkflowResponses.Instance terminate(String processInstanceId, String reason) {
        requireRuntime(processInstanceId);
        runtimeService.deleteProcessInstance(processInstanceId,
                StringUtils.defaultIfBlank(reason, "人工终止"));
        return get(processInstanceId);
    }

    @Override
    public boolean isCompleted(String processInstanceId) {
        return getState(processInstanceId) == ProcessState.COMPLETED;
    }

    @Override
    public boolean isEnded(String processInstanceId) {
        ProcessState state = getState(processInstanceId);
        return state == ProcessState.COMPLETED || state == ProcessState.TERMINATED;
    }

    private ProcessInstance requireRuntime(String processInstanceId) {
        requireId(processInstanceId, "processInstanceId");
        ProcessInstance instance = findRuntime(processInstanceId);
        if (instance != null) return instance;

        HistoricProcessInstance historic = findHistoric(processInstanceId);
        if (historic != null) {
            throw new IllegalStateException("流程已经结束，不能变更状态: " + processInstanceId);
        }
        throw new NoSuchElementException("运行中的流程实例不存在: " + processInstanceId);
    }

    private ProcessInstance findRuntime(String processInstanceId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
    }

    private HistoricProcessInstance findHistoric(String processInstanceId) {
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
    }

    private WorkflowResponses.Instance toResponse(ProcessInstance value) {
        ProcessState state = value.isSuspended() ? ProcessState.SUSPENDED : ProcessState.RUNNING;
        return new WorkflowResponses.Instance(value.getId(), value.getProcessDefinitionId(), value.getBusinessKey(),
                state.name(), value.isSuspended(), value.getStartTime(), null, null);
    }

    private WorkflowResponses.Instance toResponse(HistoricProcessInstance value) {
        ProcessState state = StringUtils.isBlank(value.getDeleteReason())
                ? ProcessState.COMPLETED : ProcessState.TERMINATED;
        return new WorkflowResponses.Instance(value.getId(), value.getProcessDefinitionId(), value.getBusinessKey(),
                state.name(), false, value.getStartTime(), value.getEndTime(), value.getDeleteReason());
    }

    private Map<String, Object> safeVariables(Map<String, Object> variables) {
        return variables == null ? Collections.emptyMap() : variables;
    }

    private void requireId(String value, String name) {
        if (StringUtils.isBlank(value)) throw new IllegalArgumentException(name + "不能为空");
    }
}
