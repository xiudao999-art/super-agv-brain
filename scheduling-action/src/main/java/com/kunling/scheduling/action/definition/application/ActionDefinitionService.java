package com.kunling.scheduling.action.definition.application;

import com.kunling.scheduling.action.config.ImmutableCollections;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.infrastructure.ActionDefinitionEntity;
import com.kunling.scheduling.action.definition.infrastructure.ActionDefinitionRepository;
import com.kunling.scheduling.action.robotbridge.application.ActionCapabilityValidator;
import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Action 定义的唯一写入口；保存接收未完成草稿，启用和执行阶段再完成业务校验。 */
@Service
public class ActionDefinitionService {

    private final ActionDefinitionRepository repository;
    private final ActionDefinitionValidator validator;
    private final ActionExecutionLock executionLock;
    private final RobotActionTransport transport;
    private final ActionCapabilityValidator capabilityValidator;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    @Autowired
    public ActionDefinitionService(ActionDefinitionRepository repository,
                                   ActionDefinitionValidator validator,
                                   ActionExecutionLock executionLock,
                                   RobotActionTransport transport,
                                   ActionCapabilityValidator capabilityValidator,
                                   JsonCodec jsonCodec) {
        this(repository, validator, executionLock, transport, capabilityValidator,
                jsonCodec, Clock.systemUTC());
    }

    ActionDefinitionService(ActionDefinitionRepository repository,
                            ActionDefinitionValidator validator,
                            ActionExecutionLock executionLock,
                            RobotActionTransport transport,
                            ActionCapabilityValidator capabilityValidator,
                            JsonCodec jsonCodec,
                            Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.executionLock = executionLock;
        this.transport = transport;
        this.capabilityValidator = capabilityValidator;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ActionDefinitionView> list() {
        return repository.findAllByOrderByNameAscIdAsc().stream()
                .map(this::toView)
                .collect(ImmutableCollections.toImmutableList());
    }

    @Transactional(readOnly = true)
    public ActionDefinitionView get(String id) {
        return toView(required(id));
    }

    @Transactional(readOnly = true)
    public ActionDefinitionView getEnabled(String id) {
        ActionDefinitionEntity entity = required(id);
        assertEnabled(entity);
        return toView(entity);
    }

    /** 仅供执行准备事务调用；该锁必须一直持有到执行记录创建完成。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public ActionDefinitionView lockEnabledForExecution(String id) {
        ActionDefinitionEntity entity = requiredForUpdate(id);
        assertEnabled(entity);
        return toView(entity);
    }

    @Transactional
    public ActionDefinitionView create(ActionDefinition definition) {
        Instant now = clock.instant();
        String id = UUID.randomUUID().toString();
        ActionDefinition persisted = new ActionDefinition(id, definition.name(), false,
                definition.timeoutMs(), definition.steps());
        return toView(repository.save(new ActionDefinitionEntity(id, persisted, jsonCodec, now)));
    }

    @Transactional
    public ActionDefinitionView update(String id, ActionDefinition definition) {
        ActionDefinitionEntity entity = requiredForUpdate(id);
        assertNotExecuting(id);
        // id 与 enabled 归服务端所有：保存时直接规范化，不因客户端草稿值不同而拒绝请求。
        ActionDefinition persisted = new ActionDefinition(id, definition.name(), entity.isEnabled(),
                definition.timeoutMs(), definition.steps());
        entity.update(persisted, jsonCodec, clock.instant());
        return toView(repository.save(entity));
    }

    @Transactional
    public ActionDefinitionView enable(String id, String robotId) {
        ActionDefinitionEntity entity = requiredForUpdate(id);
        assertNotExecuting(id);
        ActionDefinition definition = entity.definition(jsonCodec);
        validator.validateExecutable(definition);
        String normalizedRobotId = requireText(robotId, "robotId");
        RobotSessionView session = transport.findSession(normalizedRobotId)
                .orElseThrow(() -> new RobotUnavailableException("机器人当前未连接：" + normalizedRobotId));
        capabilityValidator.validate(definition, session);
        entity.changeEnabled(true, clock.instant());
        return toView(repository.save(entity));
    }

    @Transactional
    public ActionDefinitionView disable(String id) {
        ActionDefinitionEntity entity = requiredForUpdate(id);
        assertNotExecuting(id);
        entity.changeEnabled(false, clock.instant());
        return toView(repository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        ActionDefinitionEntity entity = requiredForUpdate(id);
        assertNotExecuting(id);
        repository.delete(entity);
    }

    private ActionDefinitionView toView(ActionDefinitionEntity entity) {
        Optional<String> activeExecution = executionLock
                .findActiveExecutionIdByActionDefinitionId(entity.getId());
        return new ActionDefinitionView(entity.definition(jsonCodec), activeExecution.isPresent(),
                activeExecution.orElse(null), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private void assertNotExecuting(String id) {
        Optional<String> executionId = executionLock.findActiveExecutionIdByActionDefinitionId(id);
        if (executionId.isPresent()) {
            throw new ActionConflictException("Action 正在执行，定义不可修改：" + executionId.get());
        }
    }

    private void assertEnabled(ActionDefinitionEntity entity) {
        if (!entity.isEnabled()) {
            throw new ActionConflictException("Action 当前未启用：" + entity.getId());
        }
    }

    private ActionDefinitionEntity required(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ActionNotFoundException("找不到 Action：" + id));
    }

    private ActionDefinitionEntity requiredForUpdate(String id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ActionNotFoundException("找不到 Action：" + id));
    }

    private String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空。");
        }
        return value.trim();
    }
}
