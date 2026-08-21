package com.kunling.scheduling.action.definition.application;

import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionDefinitionStatus;
import com.kunling.scheduling.action.definition.infrastructure.ActionDefinitionEntity;
import com.kunling.scheduling.action.definition.infrastructure.ActionDefinitionRepository;
import com.kunling.scheduling.action.config.ImmutableCollections;
import com.kunling.scheduling.action.config.JsonCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Action 当前配置的唯一写入口，集中执行并发校验和运行态写锁。 */
@Service
public class ActionDefinitionService {

    private final ActionDefinitionRepository repository;
    private final ActionDefinitionValidator validator;
    private final ActionExecutionLock executionLock;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    @Autowired
    public ActionDefinitionService(ActionDefinitionRepository repository,
                                   ActionDefinitionValidator validator,
                                   ActionExecutionLock executionLock,
                                   JsonCodec jsonCodec) {
        this(repository, validator, executionLock, jsonCodec, Clock.systemUTC());
    }

    ActionDefinitionService(ActionDefinitionRepository repository,
                            ActionDefinitionValidator validator,
                            ActionExecutionLock executionLock,
                            JsonCodec jsonCodec,
                            Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.executionLock = executionLock;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ActionDefinitionView> list() {
        return repository.findAllByOrderByActionKeyAsc().stream()
                .map(this::toView)
                .collect(ImmutableCollections.toImmutableList());
    }

    @Transactional(readOnly = true)
    public ActionDefinitionView get(String actionKey) {
        return toView(required(actionKey));
    }

    @Transactional(readOnly = true)
    public ActionDefinitionView getActive(String actionKey) {
        ActionDefinitionEntity entity = required(actionKey);
        if (entity.getStatus() != ActionDefinitionStatus.ACTIVE) {
            throw new ActionConflictException("Action 当前不是 ACTIVE 状态：" + actionKey);
        }
        return toView(entity);
    }

    @Transactional
    public ActionDefinitionView create(ActionDefinition definition) {
        validator.validateDraft(definition);
        if (repository.findByActionKey(definition.actionKey()).isPresent()) {
            throw new ActionConflictException("actionKey 已存在：" + definition.actionKey());
        }
        Instant now = clock.instant();
        ActionDefinitionEntity entity = new ActionDefinitionEntity(
                UUID.randomUUID().toString(), definition, jsonCodec, now);
        return toView(repository.save(entity));
    }

    @Transactional
    public ActionDefinitionView update(String actionKey, long expectedRevision, ActionDefinition definition) {
        validator.validateDraft(definition);
        if (!actionKey.equals(definition.actionKey())) {
            throw new IllegalArgumentException("路径 actionKey 与 definition.actionKey 不一致。");
        }
        assertNotExecuting(actionKey);
        ActionDefinitionEntity entity = requiredForUpdate(actionKey);
        assertRevision(entity, expectedRevision);
        entity.update(definition, jsonCodec, clock.instant());
        return toView(repository.save(entity));
    }

    @Transactional
    public ActionDefinitionView activate(String actionKey, long expectedRevision) {
        assertNotExecuting(actionKey);
        ActionDefinitionEntity entity = requiredForUpdate(actionKey);
        assertRevision(entity, expectedRevision);
        validator.validateExecutable(entity.definition(jsonCodec));
        entity.changeStatus(ActionDefinitionStatus.ACTIVE, clock.instant());
        return toView(repository.save(entity));
    }

    @Transactional
    public ActionDefinitionView disable(String actionKey, long expectedRevision) {
        assertNotExecuting(actionKey);
        ActionDefinitionEntity entity = requiredForUpdate(actionKey);
        assertRevision(entity, expectedRevision);
        entity.changeStatus(ActionDefinitionStatus.DISABLED, clock.instant());
        return toView(repository.save(entity));
    }

    @Transactional
    public void delete(String actionKey, long expectedRevision) {
        assertNotExecuting(actionKey);
        ActionDefinitionEntity entity = requiredForUpdate(actionKey);
        assertRevision(entity, expectedRevision);
        if (entity.getStatus() == ActionDefinitionStatus.ACTIVE) {
            throw new ActionConflictException("ACTIVE Action 必须先停用后才能删除。");
        }
        repository.delete(entity);
    }

    private ActionDefinitionView toView(ActionDefinitionEntity entity) {
        Optional<String> activeExecution = executionLock.findActiveExecutionIdByActionKey(entity.getActionKey());
        return new ActionDefinitionView(entity.getId(), entity.getActionKey(), entity.getRevision(),
                entity.getStatus(), entity.definition(jsonCodec), activeExecution.isPresent(),
                activeExecution.orElse(null), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private void assertNotExecuting(String actionKey) {
        Optional<String> executionId = executionLock.findActiveExecutionIdByActionKey(actionKey);
        if (executionId.isPresent()) {
            throw new ActionConflictException("Action 正在执行，页面和参数已锁定：" + executionId.get());
        }
    }

    private void assertRevision(ActionDefinitionEntity entity, long expectedRevision) {
        if (entity.getRevision() != expectedRevision) {
            throw new ActionConflictException("Action 已被其他操作修改，请刷新后重试。");
        }
    }

    private ActionDefinitionEntity required(String actionKey) {
        return repository.findByActionKey(actionKey)
                .orElseThrow(() -> new ActionNotFoundException("找不到 Action：" + actionKey));
    }

    private ActionDefinitionEntity requiredForUpdate(String actionKey) {
        return repository.findByActionKeyForUpdate(actionKey)
                .orElseThrow(() -> new ActionNotFoundException("找不到 Action：" + actionKey));
    }
}
