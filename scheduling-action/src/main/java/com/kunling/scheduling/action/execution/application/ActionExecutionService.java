package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.commissioning.application.ActionParameterSetService;
import com.kunling.scheduling.action.commissioning.application.ActionParameterSetView;
import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.definition.application.ActionDefinitionService;
import com.kunling.scheduling.action.definition.application.ActionDefinitionView;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.execution.domain.CreateActionExecutionResult;
import com.kunling.scheduling.action.execution.domain.NewActionExecution;
import com.kunling.scheduling.action.robotbridge.application.DispatchReceipt;
import com.kunling.scheduling.action.robotbridge.application.RobotActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotActionQuery;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import com.kunling.scheduling.action.shared.JsonCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 预览、冻结、持久化和完整包下发的唯一入口。 */
@Service
public class ActionExecutionService {

    private final ActionDefinitionService definitionService;
    private final ActionParameterSetService parameterSetService;
    private final ActionPackageAssembler packageAssembler;
    private final ActionExecutionStore executionStore;
    private final RobotActionTransport transport;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    @Autowired
    public ActionExecutionService(ActionDefinitionService definitionService,
                                  ActionParameterSetService parameterSetService,
                                  ActionPackageAssembler packageAssembler,
                                  ActionExecutionStore executionStore,
                                  RobotActionTransport transport,
                                  JsonCodec jsonCodec) {
        this(definitionService, parameterSetService, packageAssembler, executionStore,
                transport, jsonCodec, Clock.systemUTC());
    }

    ActionExecutionService(ActionDefinitionService definitionService,
                           ActionParameterSetService parameterSetService,
                           ActionPackageAssembler packageAssembler,
                           ActionExecutionStore executionStore,
                           RobotActionTransport transport,
                           JsonCodec jsonCodec,
                           Clock clock) {
        this.definitionService = definitionService;
        this.parameterSetService = parameterSetService;
        this.packageAssembler = packageAssembler;
        this.executionStore = executionStore;
        this.transport = transport;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    /** 草稿也可预览最终参数，但只有 ACTIVE Action 可以正式下发。 */
    public ActionPackagePreview preview(StartActionExecutionRequest request) {
        validateCommonRequest(request);
        ActionDefinitionView action = definitionService.get(request.actionKey());
        ActionParameterSetView parameterSet = findParameterSet(request);
        return packageAssembler.assemble(action, parameterSet, request.input(), request.robotId());
    }

    public ActionExecutionView start(StartActionExecutionRequest request) {
        validateCommonRequest(request);
        requireText(request.expectedPackageHash(), "expectedPackageHash");
        String actionInstanceId = request.actionInstanceId() == null
                ? UUID.randomUUID().toString() : request.actionInstanceId();
        if (actionInstanceId.length() > 128) {
            throw new IllegalArgumentException("actionInstanceId 长度不能超过 128。");
        }

        String requestHash = requestHash(request);
        Optional<ActionExecutionView> existing = executionStore.find(actionInstanceId);
        if (existing.isPresent()) {
            if (!existing.get().requestHash().equals(requestHash)) {
                throw new ActionConflictException("actionInstanceId 已绑定到不同请求。");
            }
            return existing.get();
        }

        ActionDefinitionView action = definitionService.getActive(request.actionKey());
        ActionParameterSetView parameterSet = findParameterSet(request);
        ActionPackagePreview actionPackage = packageAssembler.assemble(
                action, parameterSet, request.input(), request.robotId());
        if (!actionPackage.packageHash().equals(request.expectedPackageHash())) {
            throw new ActionConflictException("Action 或参数已发生变化，请重新预览后再执行。");
        }

        RobotSessionView session = transport.findSession(request.robotId())
                .orElseThrow(() -> new RobotUnavailableException("机器人当前未连接：" + request.robotId()));
        if (!session.acceptedActionTypes().contains(actionPackage.downstreamActionType())) {
            throw new RobotUnavailableException("机器人当前会话不支持动作："
                    + actionPackage.downstreamActionType());
        }

        Instant now = clock.instant();
        String deviceCommandId = "dc-" + jsonCodec.sha256(actionInstanceId).substring(0, 32);
        NewActionExecution newExecution = new NewActionExecution(actionInstanceId, request.robotId(),
                deviceCommandId, actionPackage.actionKey(), actionPackage.actionRevision(),
                actionPackage.downstreamActionType(), actionPackage.parameterSetId(),
                actionPackage.parameterSetRevision(), actionPackage.protocolActionVersion(), requestHash,
                actionPackage.packageHash(), request.workflowInstanceId(), request.workflowNodeInstanceId(),
                actionPackage.definitionSnapshot(), actionPackage.parameterSnapshot(),
                actionPackage.inputSnapshot(), actionPackage.commandInput(), actionPackage.timeoutMs(), now);

        CreateActionExecutionResult creation = executionStore.createIfAbsent(newExecution);
        if (!creation.created()) {
            // 幂等请求只读取既有执行，绝不重放物理动作。
            return creation.execution();
        }

        try {
            DispatchReceipt receipt = transport.dispatch(new RobotActionCommand(request.robotId(),
                    actionInstanceId, deviceCommandId, request.workflowInstanceId(),
                    request.workflowNodeInstanceId(), actionPackage.protocolActionVersion(),
                    actionPackage.packageHash(), actionPackage.commandInput(), actionPackage.timeoutMs(), now,
                    actionPackage.actionKey(), actionPackage.actionRevision(), actionPackage.parameterSetId(),
                    actionPackage.parameterSetRevision()));
            return executionStore.markDispatched(actionInstanceId, receipt.sessionId(),
                    receipt.messageId(), receipt.sentAt());
        } catch (RobotUnavailableException exception) {
            // 写失败不能证明对端是否收到，必须保持原快照并进入人工确认态。
            return executionStore.hold(actionInstanceId, "DISPATCH_RESULT_UNKNOWN",
                    exception.getMessage(), now);
        }
    }

    public ActionExecutionView get(String actionInstanceId) {
        return executionStore.get(actionInstanceId);
    }

    public Optional<ActionExecutionView> findActiveForAction(String actionKey) {
        return executionStore.findActiveExecutionIdByActionKey(actionKey).map(executionStore::get);
    }

    public ActionExecutionView query(String actionInstanceId) {
        ActionExecutionView execution = executionStore.get(actionInstanceId);
        transport.query(new RobotActionQuery(execution.robotId(), execution.actionInstanceId(),
                execution.deviceCommandId()));
        return execution;
    }

    private ActionParameterSetView findParameterSet(StartActionExecutionRequest request) {
        return request.parameterSetId() == null ? null
                : parameterSetService.getEnabledForAction(request.parameterSetId(), request.actionKey());
    }

    private String requestHash(StartActionExecutionRequest request) {
        ObjectNode fingerprint = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        fingerprint.put("robotId", request.robotId());
        fingerprint.put("actionKey", request.actionKey());
        putNullable(fingerprint, "parameterSetId", request.parameterSetId());
        fingerprint.set("input", request.input());
        fingerprint.put("expectedPackageHash", request.expectedPackageHash());
        putNullable(fingerprint, "workflowInstanceId", request.workflowInstanceId());
        putNullable(fingerprint, "workflowNodeInstanceId", request.workflowNodeInstanceId());
        return jsonCodec.sha256(jsonCodec.writeCanonical(fingerprint));
    }

    private void validateCommonRequest(StartActionExecutionRequest request) {
        if (request == null) throw new IllegalArgumentException("执行请求不能为空。");
        requireText(request.robotId(), "robotId");
        requireText(request.actionKey(), "actionKey");
        JsonNode input = request.input();
        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException("input 必须是 JSON 对象。");
        }
    }

    private void putNullable(ObjectNode target, String name, String value) {
        if (value == null) target.putNull(name); else target.put(name, value);
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空。");
        }
    }
}
