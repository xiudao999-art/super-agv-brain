package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.kunling.scheduling.action.capability.application.CapabilityContractGuard;
import com.kunling.scheduling.action.compilation.domain.ExecutionNode;
import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.definition.application.ActionControlPlaneService;
import com.kunling.scheduling.action.definition.application.ActionReleaseView;
import com.kunling.scheduling.action.definition.domain.ActionReleaseStatus;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionEntity;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionNodeEntity;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionNodeRepository;
import com.kunling.scheduling.action.execution.infrastructure.ActionExecutionRepository;
import com.kunling.scheduling.action.shared.JsonCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ActionExecutionService {

    private final ActionExecutionRepository executionRepository;
    private final ActionExecutionNodeRepository nodeRepository;
    private final ActionControlPlaneService controlPlane;
    private final CapabilityContractGuard capabilityContractGuard;
    private final ActionInputValidator inputValidator;
    private final ExecutionPlanMaterializer planMaterializer;
    private final ExecutionStateService stateService;
    private final ExecutionCoordinator coordinator;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    public ActionExecutionService(
            ActionExecutionRepository executionRepository,
            ActionExecutionNodeRepository nodeRepository,
            ActionControlPlaneService controlPlane,
            CapabilityContractGuard capabilityContractGuard,
            ActionInputValidator inputValidator,
            ExecutionPlanMaterializer planMaterializer,
            ExecutionStateService stateService,
            ExecutionCoordinator coordinator,
            JsonCodec jsonCodec) {
        this.executionRepository = executionRepository;
        this.nodeRepository = nodeRepository;
        this.controlPlane = controlPlane;
        this.capabilityContractGuard = capabilityContractGuard;
        this.inputValidator = inputValidator;
        this.planMaterializer = planMaterializer;
        this.stateService = stateService;
        this.coordinator = coordinator;
        this.jsonCodec = jsonCodec;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public ActionExecutionView start(StartActionExecutionRequest request) {
        validateRequest(request);
        String actionInstanceId = request.actionInstanceId() == null || request.actionInstanceId().isBlank()
                ? UUID.randomUUID().toString()
                : request.actionInstanceId().trim();
        ActionExecutionEntity existing = executionRepository.findById(actionInstanceId).orElse(null);
        if (existing != null) {
            if (!existing.getRobotId().equals(request.robotId())
                    || !existing.getActionKey().equals(request.actionKey())
                    || !existing.getActionVersion().equals(request.actionVersion())) {
                throw new ActionConflictException("actionInstanceId 已被其他动作占用。");
            }
            // actionInstanceId 是调度侧幂等键；重复请求只返回历史实例，不重复驱动机器人。
            return stateService.get(actionInstanceId);
        }

        ActionReleaseView release = controlPlane.getRelease(request.actionKey(), request.actionVersion());
        if (release.status() != ActionReleaseStatus.PUBLISHED) {
            throw new IllegalArgumentException("已下线版本不能发起新的 Action 执行。");
        }
        if (!release.definition().entryPoint()) {
            throw new IllegalArgumentException("组合动作不能作为调度入口直接执行。");
        }
        capabilityContractGuard.verify(release.requiredCapabilities());
        JsonNode input = request.input() == null ? JsonNodeFactory.instance.objectNode() : request.input();
        JsonNode context = request.context() == null ? JsonNodeFactory.instance.objectNode() : request.context();
        inputValidator.validate(input, release.definition().inputSchema());
        var materializedNodes = planMaterializer.materialize(release.plan(), input, context);
        Instant now = clock.instant();
        ActionExecutionEntity execution = new ActionExecutionEntity(actionInstanceId, request.robotId(),
                request.actionKey(), request.actionVersion(), request.workflowInstanceId(),
                request.workflowNodeInstanceId(), release.planHash(), jsonCodec.write(input), jsonCodec.write(context), now);
        executionRepository.save(execution);
        for (int ordinal = 0; ordinal < materializedNodes.size(); ordinal++) {
            ExecutionNode node = materializedNodes.get(ordinal);
            nodeRepository.save(new ActionExecutionNodeEntity(UUID.randomUUID().toString(), actionInstanceId,
                    ordinal, node.executionNodeId(), node.sourcePath(), node.capabilityKey(),
                    node.capabilityContractHash(), createConsumeId(actionInstanceId, ordinal)));
        }

        // 必须等数据库事务提交后再启动异步编排，避免执行线程先于实例数据可见。
        scheduleAfterCommit(actionInstanceId);
        return stateService.get(actionInstanceId);
    }

    @Transactional(readOnly = true)
    public ActionExecutionView get(String actionInstanceId) {
        return stateService.get(actionInstanceId);
    }

    @Transactional
    public ActionExecutionView cancel(String actionInstanceId) {
        return stateService.requestCancel(actionInstanceId);
    }

    private void scheduleAfterCommit(String actionInstanceId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    coordinator.submit(actionInstanceId);
                }
            });
        } else {
            coordinator.submit(actionInstanceId);
        }
    }

    private void validateRequest(StartActionExecutionRequest request) {
        if (request == null || request.robotId() == null || request.robotId().isBlank()
                || request.actionKey() == null || request.actionKey().isBlank()
                || request.actionVersion() == null || request.actionVersion().isBlank()) {
            throw new IllegalArgumentException("robotId、actionKey 和 actionVersion 不能为空。");
        }
        if (request.actionInstanceId() != null && request.actionInstanceId().length() > 128) {
            throw new IllegalArgumentException("actionInstanceId 长度不能超过 128。");
        }
        if (request.robotId().length() > 128 || request.actionKey().length() > 128
                || request.actionVersion().length() > 32
                || length(request.workflowInstanceId()) > 128
                || length(request.workflowNodeInstanceId()) > 128) {
            throw new IllegalArgumentException("执行请求中的标识长度超过接口限制。");
        }
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private String createConsumeId(String actionInstanceId, int ordinal) {
        String readable = actionInstanceId + ":" + ordinal;
        return readable.length() <= 128 ? readable : "consume-" + jsonCodec.sha256(readable);
    }
}
