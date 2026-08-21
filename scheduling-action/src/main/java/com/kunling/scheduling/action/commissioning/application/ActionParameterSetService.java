package com.kunling.scheduling.action.commissioning.application;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.kunling.scheduling.action.commissioning.infrastructure.ActionParameterSetEntity;
import com.kunling.scheduling.action.commissioning.infrastructure.ActionParameterSetRepository;
import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.definition.application.ActionDefinitionService;
import com.kunling.scheduling.action.definition.application.ActionDefinitionView;
import com.kunling.scheduling.action.definition.application.ActionExecutionLock;
import com.kunling.scheduling.action.definition.application.ActionNotFoundException;
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

/** 联调参数集的唯一写入口；执行快照使用它的值副本而不是可变引用。 */
@Service
public class ActionParameterSetService {

    private final ActionParameterSetRepository repository;
    private final ActionDefinitionService definitionService;
    private final ActionParameterValueValidator parameterValueValidator;
    private final ActionExecutionLock executionLock;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    @Autowired
    public ActionParameterSetService(ActionParameterSetRepository repository,
                                     ActionDefinitionService definitionService,
                                     ActionParameterValueValidator parameterValueValidator,
                                     ActionExecutionLock executionLock,
                                     JsonCodec jsonCodec) {
        this(repository, definitionService, parameterValueValidator, executionLock, jsonCodec, Clock.systemUTC());
    }

    ActionParameterSetService(ActionParameterSetRepository repository,
                              ActionDefinitionService definitionService,
                              ActionParameterValueValidator parameterValueValidator,
                              ActionExecutionLock executionLock,
                              JsonCodec jsonCodec,
                              Clock clock) {
        this.repository = repository;
        this.definitionService = definitionService;
        this.parameterValueValidator = parameterValueValidator;
        this.executionLock = executionLock;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ActionParameterSetView> list(String actionKey) {
        definitionService.get(actionKey);
        return repository.findByActionKeyOrderByNameAsc(actionKey).stream()
                .map(this::toView)
                .collect(ImmutableCollections.toImmutableList());
    }

    @Transactional(readOnly = true)
    public ActionParameterSetView get(String id) {
        return toView(required(id));
    }

    @Transactional(readOnly = true)
    public ActionParameterSetView getEnabledForAction(String id, String actionKey) {
        ActionParameterSetEntity entity = required(id);
        if (!entity.getActionKey().equals(actionKey)) {
            throw new IllegalArgumentException("参数集不属于 Action " + actionKey + "。");
        }
        if (!entity.isEnabled()) {
            throw new ActionConflictException("参数集已停用：" + id);
        }
        return toView(entity);
    }

    @Transactional
    public ActionParameterSetView create(SaveParameterSetRequest request) {
        validate(request);
        Instant now = clock.instant();
        ActionParameterSetEntity entity = new ActionParameterSetEntity(
                UUID.randomUUID().toString(), request, jsonCodec, now);
        return toView(repository.save(entity));
    }

    @Transactional
    public ActionParameterSetView update(String id, SaveParameterSetRequest request) {
        validate(request);
        assertNotExecuting(id);
        ActionParameterSetEntity entity = requiredForUpdate(id);
        if (request.expectedRevision() == null || entity.getRevision() != request.expectedRevision()) {
            throw new ActionConflictException("联调参数集已被其他操作修改，请刷新后重试。");
        }
        entity.update(request, jsonCodec, clock.instant());
        return toView(repository.save(entity));
    }

    @Transactional
    public void delete(String id, long expectedRevision) {
        assertNotExecuting(id);
        ActionParameterSetEntity entity = requiredForUpdate(id);
        if (entity.getRevision() != expectedRevision) {
            throw new ActionConflictException("联调参数集已被其他操作修改，请刷新后重试。");
        }
        repository.delete(entity);
    }

    private void validate(SaveParameterSetRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("参数集请求不能为空。");
        }
        requireText(request.actionKey(), "actionKey");
        requireText(request.name(), "name");
        ActionDefinitionView action = definitionService.get(request.actionKey());
        parameterValueValidator.validate(request.values() == null
                        ? JsonNodeFactory.instance.objectNode() : request.values(),
                action.definition().parameterSchema());
    }

    private ActionParameterSetView toView(ActionParameterSetEntity entity) {
        String executionId = executionLock.findActiveExecutionIdByParameterSetId(entity.getId()).orElse(null);
        return entity.toView(jsonCodec, executionId);
    }

    private void assertNotExecuting(String parameterSetId) {
        Optional<String> executionId = executionLock.findActiveExecutionIdByParameterSetId(parameterSetId);
        if (executionId.isPresent()) {
            throw new ActionConflictException("联调参数正在执行，页面和参数已锁定：" + executionId.get());
        }
    }

    private ActionParameterSetEntity required(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ActionNotFoundException("找不到联调参数集：" + id));
    }

    private ActionParameterSetEntity requiredForUpdate(String id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ActionNotFoundException("找不到联调参数集：" + id));
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空。");
        }
    }
}
