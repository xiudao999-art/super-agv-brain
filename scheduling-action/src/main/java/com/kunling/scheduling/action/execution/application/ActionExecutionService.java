package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.commissioning.application.ActionParameterSetService;
import com.kunling.scheduling.action.commissioning.application.ActionParameterSetView;
import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.definition.application.ActionDefinitionService;
import com.kunling.scheduling.action.definition.application.ActionDefinitionView;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.execution.domain.ActionExecutionEventView;
import com.kunling.scheduling.action.execution.domain.CreateActionExecutionResult;
import com.kunling.scheduling.action.execution.domain.NewActionExecution;
import com.kunling.scheduling.action.robotbridge.application.DispatchReceipt;
import com.kunling.scheduling.action.robotbridge.application.RobotActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotActionQuery;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import com.kunling.scheduling.action.config.JsonCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

/** 预览、冻结、持久化和完整包下发的唯一入口。 */
@Service
public class ActionExecutionService implements ActionExecutionGateway {

    private final ActionDefinitionService definitionService;
    private final ActionParameterSetService parameterSetService;
    private final ActionPackageAssembler packageAssembler;
    private final ActionExecutionStore executionStore;
    private final RobotActionTransport transport;
    private final JsonCodec jsonCodec;
    private final ActionExecutionReportPublisher reportPublisher;
    private final Clock clock;

    @Autowired
    public ActionExecutionService(ActionDefinitionService definitionService,
                                  ActionParameterSetService parameterSetService,
                                  ActionPackageAssembler packageAssembler,
                                  ActionExecutionStore executionStore,
                                  RobotActionTransport transport,
                                  JsonCodec jsonCodec,
                                  ActionExecutionReportPublisher reportPublisher) {
        this(definitionService, parameterSetService, packageAssembler, executionStore,
                transport, jsonCodec, reportPublisher, Clock.systemUTC());
    }

    ActionExecutionService(ActionDefinitionService definitionService,
                           ActionParameterSetService parameterSetService,
                           ActionPackageAssembler packageAssembler,
                           ActionExecutionStore executionStore,
                           RobotActionTransport transport,
                           JsonCodec jsonCodec,
                           ActionExecutionReportPublisher reportPublisher,
                           Clock clock) {
        this.definitionService = definitionService;
        this.parameterSetService = parameterSetService;
        this.packageAssembler = packageAssembler;
        this.executionStore = executionStore;
        this.transport = transport;
        this.jsonCodec = jsonCodec;
        this.reportPublisher = reportPublisher;
        this.clock = clock;
    }

    /** 草稿也可预览最终参数，但只有 ACTIVE Action 可以正式下发。 */
    public ActionPackagePreview preview(StartActionExecutionRequest request) {
        validateCommonRequest(request);
        ActionDefinitionView action = definitionService.get(request.actionKey());
        ActionParameterSetView parameterSet = findParameterSet(request);
        return packageAssembler.assemble(action, parameterSet, request.input(), request.robotId());
    }

    /**
     * 执行引擎本地调用入口。packageHash 仅在本模块内产生和消费，调用方无需理解预览协议。
     */
    @Override
    public ActionExecutionReceipt execute(ExecuteActionCommand command) {
        validateEngineCommand(command);
        StartActionExecutionRequest previewRequest = new StartActionExecutionRequest(
                command.actionInstanceId(), command.robotId(), command.actionKey(), command.parameterSetId(),
                command.input(), null, command.workflowInstanceId(), command.workflowNodeInstanceId());
        ActionPackagePreview actionPackage = preview(previewRequest);
        StartActionExecutionRequest executionRequest = new StartActionExecutionRequest(
                command.actionInstanceId(), command.robotId(), command.actionKey(), command.parameterSetId(),
                command.input(), actionPackage.packageHash(),
                command.workflowInstanceId(), command.workflowNodeInstanceId());
        ActionExecutionView execution = start(executionRequest);
        return new ActionExecutionReceipt(execution.actionInstanceId(), execution.createdAt());
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
            ActionExecutionView held = executionStore.hold(actionInstanceId, "DISPATCH_RESULT_UNKNOWN",
                    exception.getMessage(), now);
            reportPublisher.publishLocalState(held, now);
            return held;
        }
    }

    public ActionExecutionView get(String actionInstanceId) {
        return executionStore.get(actionInstanceId);
    }

    /** 返回按服务端接收顺序排列的下游执行事实，供联调页面还原完整时间线。 */
    public List<ActionExecutionEventView> getEvents(String actionInstanceId, int limit) {
        return executionStore.getEvents(actionInstanceId, limit);
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

    private void validateEngineCommand(ExecuteActionCommand command) {
        if (command == null) throw new IllegalArgumentException("Action 执行命令不能为空。");
        requireText(command.workflowInstanceId(), "workflowInstanceId");
        requireText(command.workflowNodeInstanceId(), "workflowNodeInstanceId");
        requireText(command.actionInstanceId(), "actionInstanceId");
        requireMaximumLength(command.workflowInstanceId(), "workflowInstanceId", 128);
        requireMaximumLength(command.workflowNodeInstanceId(), "workflowNodeInstanceId", 128);
        requireMaximumLength(command.actionInstanceId(), "actionInstanceId", 128);
    }

    private void requireMaximumLength(String value, String field, int maximumLength) {
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " 长度不能超过 " + maximumLength + "。");
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
