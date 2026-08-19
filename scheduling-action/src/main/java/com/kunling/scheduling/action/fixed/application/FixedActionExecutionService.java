package com.kunling.scheduling.action.fixed.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.fixed.domain.FixedActionType;
import com.kunling.scheduling.action.fixed.domain.NewRobotActionExecution;
import com.kunling.scheduling.action.fixed.domain.RobotActionExecutionView;
import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.shared.JsonCodec;
import com.kunling.scheduling.action.robotbridge.application.RobotActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotActionQuery;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class FixedActionExecutionService {

    private final FixedActionPackageCatalog packageCatalog;
    private final RobotActionExecutionStore executionStore;
    private final RobotActionTransport transport;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    @Autowired
    public FixedActionExecutionService(FixedActionPackageCatalog packageCatalog,
                                       RobotActionExecutionStore executionStore,
                                       RobotActionTransport transport,
                                       JsonCodec jsonCodec) {
        this(packageCatalog, executionStore, transport, jsonCodec, Clock.systemUTC());
    }

    public FixedActionExecutionService(FixedActionPackageCatalog packageCatalog,
                                       RobotActionExecutionStore executionStore,
                                       RobotActionTransport transport,
                                       JsonCodec jsonCodec,
                                       Clock clock) {
        this.packageCatalog = packageCatalog;
        this.executionStore = executionStore;
        this.transport = transport;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    public RobotActionExecutionView start(StartFixedActionExecutionRequest request) {
        validateRequest(request);
        FixedActionType actionType = FixedActionType.fromWireName(request.actionType());
        com.kunling.scheduling.action.fixed.domain.MaterializedFixedActionPackage actionPackage =
                packageCatalog.materialize(actionType, request.input());
        Instant now = clock.instant();
        String actionInstanceId = normalizeActionInstanceId(request.actionInstanceId());
        String deviceCommandId = "dc-" + jsonCodec.sha256(actionInstanceId).substring(0, 32);
        String requestHash = requestHash(request, actionType, actionPackage.packageHash());
        NewRobotActionExecution newExecution = new NewRobotActionExecution(
                actionInstanceId, request.robotId(), deviceCommandId,
                actionType, actionPackage.actionVersion(), actionPackage.templateVersion(), requestHash,
                actionPackage.packageHash(), normalizeOptional(request.workflowInstanceId()),
                normalizeOptional(request.workflowNodeInstanceId()), request.input(), actionPackage.commandInput(),
                actionPackage.timeoutMs(), now);

        java.util.Optional<com.kunling.scheduling.action.fixed.domain.RobotActionExecutionView> existing =
                executionStore.find(actionInstanceId);
        if (existing.isPresent()) {
            if (!existing.get().requestHash().equals(requestHash)) {
                throw new ActionConflictException(
                        "actionInstanceId 已绑定到不同的机器人、动作包或工作流上下文");
            }
            return existing.get();
        }

        RobotSessionView session = transport.findSession(request.robotId())
                .orElseThrow(() -> new RobotUnavailableException("机器人当前未连接: " + request.robotId()));
        if (!session.acceptedActionTypes().contains(actionType.wireName())) {
            throw new RobotUnavailableException("机器人当前会话不支持动作: " + actionType.wireName());
        }
        com.kunling.scheduling.action.fixed.domain.CreateRobotActionExecutionResult creation =
                executionStore.createIfAbsent(newExecution);
        if (!creation.created()) {
            // 同一实例只返回已有结果，任何状态都禁止在此处自动重放物理动作。
            return creation.execution();
        }

        try {
            com.kunling.scheduling.action.robotbridge.application.DispatchReceipt receipt =
                    transport.dispatch(new RobotActionCommand(request.robotId(), actionInstanceId,
                    deviceCommandId, newExecution.workflowInstanceId(), newExecution.workflowNodeInstanceId(),
                    actionPackage.actionVersion(), actionPackage.packageHash(), actionPackage.commandInput(),
                    actionPackage.timeoutMs(), now));
            return executionStore.markDispatched(actionInstanceId, receipt.sessionId(), receipt.messageId(),
                    receipt.sentAt());
        } catch (RobotUnavailableException exception) {
            // 网络写失败无法证明对端是否已收包，必须 HOLD，不能在重试请求中再次发送。
            return executionStore.hold(actionInstanceId, "DISPATCH_RESULT_UNKNOWN", exception.getMessage(), now);
        }
    }

    public RobotActionExecutionView get(String actionInstanceId) {
        return executionStore.get(actionInstanceId);
    }

    public RobotActionExecutionView query(String actionInstanceId) {
        RobotActionExecutionView execution = executionStore.get(actionInstanceId);
        transport.query(new RobotActionQuery(execution.robotId(), execution.actionInstanceId(),
                execution.deviceCommandId()));
        return execution;
    }

    private String requestHash(StartFixedActionExecutionRequest request, FixedActionType actionType,
                               String packageHash) {
        ObjectNode fingerprint = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        fingerprint.put("robotId", request.robotId());
        fingerprint.put("actionType", actionType.wireName());
        fingerprint.put("packageHash", packageHash);
        putOptional(fingerprint, "workflowInstanceId", request.workflowInstanceId());
        putOptional(fingerprint, "workflowNodeInstanceId", request.workflowNodeInstanceId());
        return jsonCodec.sha256(jsonCodec.writeCanonical(fingerprint));
    }

    private void validateRequest(StartFixedActionExecutionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        requireText(request.robotId(), "robotId");
        requireText(request.actionType(), "actionType");
        if (request.actionInstanceId() != null && request.actionInstanceId().length() > 128) {
            throw new IllegalArgumentException("actionInstanceId 长度不能超过 128");
        }
    }

    private String normalizeActionInstanceId(String value) {
        return value == null || value.trim().isEmpty() ? UUID.randomUUID().toString() : value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private void putOptional(ObjectNode node, String field, String value) {
        String normalized = normalizeOptional(value);
        if (normalized != null) {
            node.put(field, normalized);
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}
