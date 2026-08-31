package com.kunling.scheduling.action.execution.application;

import com.kunling.scheduling.action.definition.application.ActionDefinitionService;
import com.kunling.scheduling.action.definition.application.ActionDefinitionView;
import com.kunling.scheduling.action.execution.application.ActionExecutionPreparationService.PreparedExecution;
import com.kunling.scheduling.action.execution.domain.ActionExecutionEventView;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.robotbridge.application.ActionCapabilityValidator;
import com.kunling.scheduling.action.robotbridge.application.DispatchReceipt;
import com.kunling.scheduling.action.robotbridge.application.RobotActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Action 预览、执行准备和单次下发的唯一外部入口。 */
@Service
public class ActionExecutionService implements ActionExecutionGateway {
    private final ActionDefinitionService definitionService;
    private final ActionPackageAssembler packageAssembler;
    private final ActionExecutionPreparationService preparationService;
    private final ActionExecutionStore executionStore;
    private final RobotActionTransport transport;
    private final ActionCapabilityValidator capabilityValidator;
    private final ActionExecutionReportPublisher reportPublisher;
    private final Clock clock;

    @Autowired
    public ActionExecutionService(ActionDefinitionService definitionService,
                                  ActionPackageAssembler packageAssembler,
                                  ActionExecutionPreparationService preparationService,
                                  ActionExecutionStore executionStore,
                                  RobotActionTransport transport,
                                  ActionCapabilityValidator capabilityValidator,
                                  ActionExecutionReportPublisher reportPublisher) {
        this(definitionService, packageAssembler, preparationService, executionStore,
                transport, capabilityValidator, reportPublisher, Clock.systemUTC());
    }

    ActionExecutionService(ActionDefinitionService definitionService,
                           ActionPackageAssembler packageAssembler,
                           ActionExecutionPreparationService preparationService,
                           ActionExecutionStore executionStore,
                           RobotActionTransport transport,
                           ActionCapabilityValidator capabilityValidator,
                           ActionExecutionReportPublisher reportPublisher,
                           Clock clock) {
        this.definitionService = definitionService;
        this.packageAssembler = packageAssembler;
        this.preparationService = preparationService;
        this.executionStore = executionStore;
        this.transport = transport;
        this.capabilityValidator = capabilityValidator;
        this.reportPublisher = reportPublisher;
        this.clock = clock;
    }

    /** 预览不创建执行记录，但仍按当前在线机器人能力执行完整校验。 */
    public ActionPackagePreview preview(ActionPackagePreviewRequest request) {
        validatePreview(request);
        ActionDefinitionView action = definitionService.get(request.actionDefinitionId());
        RobotSessionView session = transport.findSession(request.robotId())
                .orElseThrow(() -> new RobotUnavailableException(
                        "机器人当前未连接：" + request.robotId()));
        capabilityValidator.validate(action.definition(), session);
        return packageAssembler.assemble(action.definition());
    }

    @Override
    public ActionExecutionReceipt execute(ExecuteActionCommand command) {
        PreparedExecution prepared = preparationService.prepare(command);
        if (!prepared.created()) {
            // 同一实例的重复调用只返回既有事实，不再发送 COMMAND。
            return new ActionExecutionReceipt(prepared.execution().actionInstanceId());
        }

        ActionExecutionView execution = prepared.execution();
        Instant now = clock.instant();
        try {
            DispatchReceipt receipt = transport.dispatch(new RobotActionCommand(
                    execution.robotId(), execution.actionInstanceId(), execution.deviceCommandId(),
                    execution.protocolVersion(), execution.packageHash(),
                    execution.commandInput(), execution.timeoutMs(), now));
            executionStore.markDispatched(execution.actionInstanceId(), receipt.sessionId(),
                    receipt.messageId(), receipt.sentAt());
        } catch (RobotUnavailableException exception) {
            // 写失败不能证明对端未收到，必须进入人工确认边界且绝不重发原实例。
            ActionExecutionView held = executionStore.hold(execution.actionInstanceId(),
                    "DISPATCH_RESULT_UNKNOWN", exception.getMessage(), now);
            reportPublisher.publishLocalState(held, now);
        }
        return new ActionExecutionReceipt(execution.actionInstanceId());
    }

    public ActionExecutionView get(String actionInstanceId) {
        return executionStore.get(actionInstanceId);
    }

    public List<ActionExecutionEventView> getEvents(String actionInstanceId, int limit) {
        return executionStore.getEvents(actionInstanceId, limit);
    }

    public Optional<ActionExecutionView> findActiveForAction(String actionDefinitionId) {
        return executionStore.findActiveExecutionIdByActionDefinitionId(actionDefinitionId)
                .map(executionStore::get);
    }

    private void validatePreview(ActionPackagePreviewRequest request) {
        if (request == null) throw new IllegalArgumentException("预览请求不能为空。");
        requireText(request.actionDefinitionId(), "actionDefinitionId");
        requireText(request.robotId(), "robotId");
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空。");
        }
    }
}
