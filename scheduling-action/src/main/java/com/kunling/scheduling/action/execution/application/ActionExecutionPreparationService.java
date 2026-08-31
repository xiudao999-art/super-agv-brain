package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.definition.application.ActionDefinitionService;
import com.kunling.scheduling.action.definition.application.ActionDefinitionView;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.execution.domain.CreateActionExecutionResult;
import com.kunling.scheduling.action.execution.domain.NewActionExecution;
import com.kunling.scheduling.action.robotbridge.application.ActionCapabilityValidator;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import lombok.Value;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 在同一数据库事务中锁定 Action 定义并创建执行记录。
 * 网络发送不属于本事务，避免持有数据库行锁等待 I/O。
 */
@Service
public class ActionExecutionPreparationService {
    private final ActionDefinitionService definitionService;
    private final ActionPackageAssembler packageAssembler;
    private final ActionExecutionStore executionStore;
    private final RobotActionTransport transport;
    private final ActionCapabilityValidator capabilityValidator;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    @Autowired
    public ActionExecutionPreparationService(ActionDefinitionService definitionService,
                                             ActionPackageAssembler packageAssembler,
                                             ActionExecutionStore executionStore,
                                             RobotActionTransport transport,
                                             ActionCapabilityValidator capabilityValidator,
                                             JsonCodec jsonCodec) {
        this(definitionService, packageAssembler, executionStore, transport,
                capabilityValidator, jsonCodec, Clock.systemUTC());
    }

    ActionExecutionPreparationService(ActionDefinitionService definitionService,
                                      ActionPackageAssembler packageAssembler,
                                      ActionExecutionStore executionStore,
                                      RobotActionTransport transport,
                                      ActionCapabilityValidator capabilityValidator,
                                      JsonCodec jsonCodec,
                                      Clock clock) {
        this.definitionService = definitionService;
        this.packageAssembler = packageAssembler;
        this.executionStore = executionStore;
        this.transport = transport;
        this.capabilityValidator = capabilityValidator;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    @Transactional
    public PreparedExecution prepare(ExecuteActionCommand command) {
        validate(command);
        ActionDefinitionView action = definitionService
                .lockEnabledForExecution(command.actionDefinitionId());

        Optional<ActionExecutionView> existing = executionStore.find(command.actionInstanceId());
        if (existing.isPresent()) {
            validateExisting(existing.get(), command);
            return new PreparedExecution(false, existing.get(), null);
        }

        RobotSessionView session = transport.findSession(command.robotId())
                .orElseThrow(() -> new RobotUnavailableException(
                        "机器人当前未连接：" + command.robotId()));
        capabilityValidator.validate(action.definition(), session);
        ActionPackagePreview actionPackage = packageAssembler.assemble(action.definition());

        Instant now = clock.instant();
        String requestHash = requestHash(command);
        String deviceCommandId = "dc-" + jsonCodec.sha256(command.actionInstanceId()).substring(0, 32);
        NewActionExecution newExecution = new NewActionExecution(
                command.actionInstanceId(), command.actionDefinitionId(), command.robotId(),
                deviceCommandId, ActionPackageAssembler.PROTOCOL_VERSION, requestHash,
                actionPackage.packageHash(), actionPackage.commandInput(),
                actionPackage.timeoutMs(), now);
        CreateActionExecutionResult creation = executionStore.createIfAbsent(newExecution);
        if (!creation.created()) validateExisting(creation.execution(), command);
        return new PreparedExecution(creation.created(), creation.execution(), actionPackage);
    }

    private String requestHash(ExecuteActionCommand command) {
        ObjectNode fingerprint = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        fingerprint.put("actionInstanceId", command.actionInstanceId());
        fingerprint.put("actionDefinitionId", command.actionDefinitionId());
        fingerprint.put("robotId", command.robotId());
        return jsonCodec.sha256(jsonCodec.writeCanonical(fingerprint));
    }

    private void validateExisting(ActionExecutionView existing, ExecuteActionCommand command) {
        if (!Objects.equals(existing.actionDefinitionId(), command.actionDefinitionId())
                || !Objects.equals(existing.robotId(), command.robotId())) {
            throw new ActionConflictException("actionInstanceId 已绑定到不同的 Action 定义或机器人。");
        }
    }

    private void validate(ExecuteActionCommand command) {
        if (command == null) throw new IllegalArgumentException("Action 执行命令不能为空。");
        requireText(command.actionInstanceId(), "actionInstanceId", 128);
        requireText(command.actionDefinitionId(), "actionDefinitionId", 36);
        requireText(command.robotId(), "robotId", 128);
    }

    private void requireText(String value, String field, int maximumLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空。");
        }
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " 长度不能超过 " + maximumLength + "。");
        }
    }

    @Value
    @Accessors(fluent = true)
    public static class PreparedExecution {
        boolean created;
        ActionExecutionView execution;
        ActionPackagePreview actionPackage;
    }
}
