package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.compilation.domain.ExecutionNode;
import com.kunling.scheduling.action.config.ActionModuleDefaults;
import com.kunling.scheduling.action.definition.application.ActionControlPlaneService;
import com.kunling.scheduling.action.definition.application.ActionReleaseView;
import com.kunling.scheduling.action.execution.domain.ExecutionError;
import com.kunling.scheduling.action.shared.NamedDaemonThreadFactory;
import com.kunling.scheduling.action.upstream.application.AtomicActionGateway;
import com.kunling.scheduling.action.upstream.application.AtomicActionOutcome;
import com.kunling.scheduling.action.upstream.application.AtomicActionRequest;
import com.kunling.scheduling.action.upstream.application.AtomicActionResult;
import com.kunling.scheduling.action.upstream.application.UpstreamUnavailableException;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Component
public class ExecutionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ExecutionCoordinator.class);

    private final ExecutionStateService stateService;
    private final ActionControlPlaneService controlPlane;
    private final AtomicActionGateway atomicActionGateway;
    private final ExecutionValueResolver valueResolver;
    private final ExecutionPlanMaterializer planMaterializer;
    private final ExecutorService executor = Executors.newFixedThreadPool(
            ActionModuleDefaults.ACTION_EXECUTION_WORKER_THREADS,
            new NamedDaemonThreadFactory("action-execution-")
    );
    private final Map<String, Semaphore> robotLocks = new ConcurrentHashMap<>();

    public ExecutionCoordinator(
            ExecutionStateService stateService,
            ActionControlPlaneService controlPlane,
            AtomicActionGateway atomicActionGateway,
            ExecutionValueResolver valueResolver,
            ExecutionPlanMaterializer planMaterializer) {
        this.stateService = stateService;
        this.controlPlane = controlPlane;
        this.atomicActionGateway = atomicActionGateway;
        this.valueResolver = valueResolver;
        this.planMaterializer = planMaterializer;
    }

    public void submit(String actionInstanceId) {
        executor.submit(() -> execute(actionInstanceId));
    }

    private void execute(String actionInstanceId) {
        ActionExecutionView execution;
        try {
            execution = stateService.get(actionInstanceId);
        } catch (RuntimeException exception) {
            log.error("加载 Action 执行实例 {} 失败", actionInstanceId, exception);
            return;
        }
        Semaphore robotLock = robotLocks.computeIfAbsent(execution.robotId(), ignored -> new Semaphore(1));
        boolean acquired = false;
        try {
            // 调度端和机器人端都保持单机器人串行；双层保护可避免两个 Action 的原子节点交错。
            robotLock.acquire();
            acquired = true;
            executeLocked(execution);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            holdExecution(execution.actionInstanceId(), "ORCHESTRATION_INTERRUPTED", "编排线程被中断，物理结果需要确认。");
        } catch (RuntimeException exception) {
            log.error("Action 执行实例 {} 编排异常", actionInstanceId, exception);
            holdExecution(execution.actionInstanceId(), "ORCHESTRATION_EXCEPTION", exception.getMessage());
        } finally {
            if (acquired) {
                robotLock.release();
            }
        }
    }

    private void executeLocked(ActionExecutionView execution) throws InterruptedException {
        ActionReleaseView release = controlPlane.getRelease(execution.actionKey(), execution.actionVersion());
        if (!release.planHash().equals(execution.planHash())) {
            holdExecution(execution.actionInstanceId(), "PLAN_HASH_MISMATCH", "执行实例引用的发布计划哈希不一致。");
            return;
        }

        Map<String, JsonNode> stepOutputs = new LinkedHashMap<>();
        ObjectNode allOutputs = JsonNodeFactory.instance.objectNode();
        JsonNode lastOutput = null;
        java.util.List<ExecutionNode> materializedNodes = planMaterializer.materialize(
                release.plan(), execution.input(), execution.context()
        );
        if (materializedNodes.size() != execution.resolvedSteps().size()) {
            holdExecution(execution.actionInstanceId(), "MATERIALIZED_PLAN_MISMATCH",
                    "执行实例节点数量与发布计划物化结果不一致。");
            return;
        }
        for (int ordinal = 0; ordinal < materializedNodes.size(); ordinal++) {
            if (stateService.isCancelRequested(execution.actionInstanceId())) {
                stateService.cancelRemaining(execution.actionInstanceId());
                return;
            }
            ExecutionNode node = materializedNodes.get(ordinal);
            JsonNode resolvedInput;
            try {
                resolvedInput = valueResolver.resolveBindings(node.bindings(), execution.input(),
                        execution.context(), stepOutputs);
            } catch (IllegalArgumentException exception) {
                ExecutionError error = new ExecutionError("BINDING_RESOLUTION_FAILED", exception.getMessage(),
                        true, false, null, "检查 Action 节点参数表达式。" );
                stateService.fail(execution.actionInstanceId(), ordinal, error, null);
                return;
            }

            stateService.startNode(execution.actionInstanceId(), ordinal, resolvedInput);
            AtomicActionResult result;
            try {
                String consumeId = execution.resolvedSteps().get(ordinal).consumeId();
                result = atomicActionGateway.execute(new AtomicActionRequest(execution.robotId(),
                        consumeId,
                        execution.workflowInstanceId(), execution.workflowNodeInstanceId(),
                        node.capabilityKey(), resolvedInput, node.timeoutMs()));
            } catch (UpstreamUnavailableException exception) {
                // 连接可能在命令发出后才断开，不能据此假定设备没有动作，统一进入 HOLD。
                ExecutionError error = new ExecutionError("ROBOT_SESSION_LOST", exception.getMessage(),
                        false, false, null, "检查机器人现场状态后人工处置。" );
                stateService.hold(execution.actionInstanceId(), ordinal, error, null);
                return;
            }

            if (result.outcome() == AtomicActionOutcome.SUCCEEDED) {
                stateService.succeedNode(execution.actionInstanceId(), ordinal, result.output(), result.evidence());
                stepOutputs.put(node.executionNodeId(), result.output() == null
                        ? JsonNodeFactory.instance.nullNode() : result.output());
                allOutputs.set(node.executionNodeId(), result.output() == null
                        ? JsonNodeFactory.instance.nullNode() : result.output());
                lastOutput = result.output();
                continue;
            }

            ExecutionError error = result.error() == null
                    ? defaultAtomicError(result.outcome())
                    : result.error();
            if (result.outcome() == AtomicActionOutcome.UNKNOWN || !error.physicalResultKnown()) {
                stateService.hold(execution.actionInstanceId(), ordinal, error, result.evidence());
            } else {
                // 一期异常策略由后续工作流承接；这里终止当前 Action，不在编排器内自动重试或跳过。
                stateService.fail(execution.actionInstanceId(), ordinal, error, result.evidence());
            }
            return;
        }

        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.set("nodes", allOutputs);
        result.set("lastOutput", lastOutput == null ? JsonNodeFactory.instance.nullNode() : lastOutput);
        stateService.complete(execution.actionInstanceId(), result);
    }

    private ExecutionError defaultAtomicError(AtomicActionOutcome outcome) {
        boolean known = outcome != AtomicActionOutcome.UNKNOWN;
        return new ExecutionError("ATOMIC_ACTION_FAILED", "上游返回失败但未提供错误详情。",
                known, false, null, known ? "检查节点输入和上游设备告警。" : "现场确认后人工处置。" );
    }

    private void holdExecution(String actionInstanceId, String code, String message) {
        stateService.holdExecution(actionInstanceId, new ExecutionError(code,
                message == null || message.trim().isEmpty() ? "编排过程出现未分类异常。" : message,
                false, false, null, "检查机器人现场状态后人工处置。"));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
