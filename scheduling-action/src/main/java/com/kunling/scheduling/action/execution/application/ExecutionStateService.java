package com.kunling.scheduling.action.execution.application;

import com.kunling.scheduling.action.shared.ImmutableCollections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.execution.domain.ExecutionError;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionEntity;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionNodeEntity;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionNodeRepository;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionRepository;
import com.kunling.scheduling.action.shared.JsonCodec;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class ExecutionStateService {

    private final ActionExecutionRepository executionRepository;
    private final ActionExecutionNodeRepository nodeRepository;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    @Autowired
    public ExecutionStateService(
            ActionExecutionRepository executionRepository,
            ActionExecutionNodeRepository nodeRepository,
            JsonCodec jsonCodec) {
        this(executionRepository, nodeRepository, jsonCodec, Clock.systemUTC());
    }

    ExecutionStateService(ActionExecutionRepository executionRepository,
                          ActionExecutionNodeRepository nodeRepository,
                          JsonCodec jsonCodec,
                          Clock clock) {
        this.executionRepository = executionRepository;
        this.nodeRepository = nodeRepository;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ActionExecutionView get(String actionInstanceId) {
        ActionExecutionEntity execution = executionRepository.findById(actionInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("找不到 Action 执行实例 " + actionInstanceId));
        return toView(execution, nodeRepository.findByActionInstanceIdOrderByNodeOrdinalAsc(actionInstanceId));
    }

    @Transactional
    public void startNode(String actionInstanceId, int ordinal, JsonNode resolvedInput) {
        Instant now = clock.instant();
        ActionExecutionEntity execution = requireExecutionForUpdate(actionInstanceId);
        ensureActive(execution);
        ActionExecutionNodeEntity node = requireNode(actionInstanceId, ordinal);
        node.start(jsonCodec.write(resolvedInput), now);
        execution.running(node.getExecutionNodeId(), now);
        nodeRepository.save(node);
        executionRepository.save(execution);
    }

    @Transactional
    public void succeedNode(String actionInstanceId, int ordinal, JsonNode output, JsonNode evidence) {
        ActionExecutionNodeEntity node = requireNode(actionInstanceId, ordinal);
        node.succeed(writeNullable(output), writeNullable(evidence), clock.instant());
        nodeRepository.save(node);
    }

    @Transactional
    public void fail(String actionInstanceId, int ordinal, ExecutionError error, JsonNode evidence) {
        Instant now = clock.instant();
        ActionExecutionEntity execution = requireExecutionForUpdate(actionInstanceId);
        ActionExecutionNodeEntity node = requireNode(actionInstanceId, ordinal);
        node.fail(jsonCodec.write(error), writeNullable(evidence), now);
        execution.fail(jsonCodec.write(error), now);
        nodeRepository.save(node);
        executionRepository.save(execution);
    }

    @Transactional
    public void hold(String actionInstanceId, int ordinal, ExecutionError error, JsonNode evidence) {
        Instant now = clock.instant();
        ActionExecutionEntity execution = requireExecutionForUpdate(actionInstanceId);
        ActionExecutionNodeEntity node = requireNode(actionInstanceId, ordinal);
        node.hold(jsonCodec.write(error), writeNullable(evidence), now);
        execution.hold(jsonCodec.write(error), now);
        nodeRepository.save(node);
        executionRepository.save(execution);
    }

    @Transactional
    public void holdExecution(String actionInstanceId, ExecutionError error) {
        ActionExecutionEntity execution = requireExecutionForUpdate(actionInstanceId);
        if (!execution.getState().isTerminal()) {
            execution.hold(jsonCodec.write(error), clock.instant());
            executionRepository.save(execution);
        }
    }

    @Transactional
    public void complete(String actionInstanceId, JsonNode result) {
        ActionExecutionEntity execution = requireExecutionForUpdate(actionInstanceId);
        ensureActive(execution);
        execution.complete(writeNullable(result), clock.instant());
        executionRepository.save(execution);
    }

    @Transactional
    public ActionExecutionView requestCancel(String actionInstanceId) {
        ActionExecutionEntity execution = requireExecutionForUpdate(actionInstanceId);
        if (!execution.getState().isTerminal()) {
            execution.requestCancel(clock.instant());
            executionRepository.save(execution);
        }
        return toView(execution, nodeRepository.findByActionInstanceIdOrderByNodeOrdinalAsc(actionInstanceId));
    }

    @Transactional(readOnly = true)
    public boolean isCancelRequested(String actionInstanceId) {
        return executionRepository.findById(actionInstanceId)
                .map(ActionExecutionEntity::isCancelRequested)
                .orElse(true);
    }

    @Transactional
    public void cancelRemaining(String actionInstanceId) {
        Instant now = clock.instant();
        ActionExecutionEntity execution = requireExecutionForUpdate(actionInstanceId);
        nodeRepository.findByActionInstanceIdOrderByNodeOrdinalAsc(actionInstanceId).forEach(node -> {
            node.cancel(now);
            nodeRepository.save(node);
        });
        execution.cancel(now);
        executionRepository.save(execution);
    }

    @Transactional
    public int holdInterruptedExecutions() {
        List<ActionExecutionEntity> interrupted = executionRepository.findByStateIn(
                ImmutableCollections.setOf(ActionExecutionState.ACCEPTED, ActionExecutionState.RUNNING));
        Instant now = clock.instant();
        ExecutionError error = new ExecutionError("ORCHESTRATOR_RESTARTED",
                "服务重启时执行尚未结束，物理结果需要人工确认。", false, false, null,
                "核对机器人现场状态后再由工作流处置。");
        interrupted.forEach(execution -> execution.hold(jsonCodec.write(error), now));
        executionRepository.saveAll(interrupted);
        return interrupted.size();
    }

    private ActionExecutionEntity requireExecutionForUpdate(String id) {
        return executionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到 Action 执行实例 " + id));
    }

    private ActionExecutionNodeEntity requireNode(String actionInstanceId, int ordinal) {
        return nodeRepository.findByActionInstanceIdAndNodeOrdinal(actionInstanceId, ordinal)
                .orElseThrow(() -> new IllegalArgumentException("找不到执行节点 " + actionInstanceId + "#" + ordinal));
    }

    private void ensureActive(ActionExecutionEntity execution) {
        if (execution.getState().isTerminal()) {
            throw new IllegalStateException("Action 执行实例已终止：" + execution.getState());
        }
    }

    private ActionExecutionView toView(ActionExecutionEntity execution, List<ActionExecutionNodeEntity> nodes) {
        return new ActionExecutionView(execution.getActionInstanceId(), execution.getRobotId(),
                execution.getActionKey(), execution.getActionVersion(), execution.getWorkflowInstanceId(),
                execution.getWorkflowNodeInstanceId(), execution.getPlanHash(), execution.getState(),
                execution.isPhysicalResultKnown(), execution.getCurrentNodeId(), readRequired(execution.getInputJson()),
                readRequired(execution.getContextJson()), readNullable(execution.getResultJson()),
                readError(execution.getErrorJson()), execution.isCancelRequested(),
                nodes.stream().map(this::toView).collect(ImmutableCollections.toImmutableList()), execution.getCreatedAt(), execution.getUpdatedAt(),
                execution.getCompletedAt());
    }

    private ActionNodeExecutionView toView(ActionExecutionNodeEntity node) {
        return new ActionNodeExecutionView(java.util.UUID.fromString(node.getId()), node.getNodeOrdinal(),
                node.getExecutionNodeId(), node.getSourcePath(), node.getCapabilityKey(),
                node.getCapabilityContractHash(), node.getState(), node.getAttempt(), node.getConsumeId(),
                readRequired(node.getResolvedInputJson()), readNullable(node.getOutputJson()),
                readNullable(node.getEvidenceJson()), readError(node.getErrorJson()), node.getStartedAt(),
                node.getCompletedAt());
    }

    private JsonNode readRequired(String json) {
        return json == null ? JsonNodeFactory.instance.objectNode() : jsonCodec.readTree(json);
    }

    private JsonNode readNullable(String json) {
        return json == null ? null : jsonCodec.readTree(json);
    }

    private ExecutionError readError(String json) {
        return json == null ? null : jsonCodec.read(json, ExecutionError.class);
    }

    private String writeNullable(JsonNode node) {
        return node == null ? null : jsonCodec.write(node);
    }
}
