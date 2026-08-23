package com.kunling.scheduling.action.exceptionmapping.application;

import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.exceptionmapping.domain.ActionErrorMappingRule;
import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleStatus;
import com.kunling.scheduling.action.exceptionmapping.infrastructure.ActionErrorMappingRuleEntity;
import com.kunling.scheduling.action.exceptionmapping.infrastructure.ActionErrorMappingRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;

/** 映射规则管理的唯一入口；只有启用规则会进入新执行快照。 */
@Service
public class ActionErrorMappingRuleService {
    private final ActionErrorMappingRuleRepository repository;
    private final ActionErrorMappingRuleValidator validator;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    @Autowired
    public ActionErrorMappingRuleService(ActionErrorMappingRuleRepository repository,
                                         ActionErrorMappingRuleValidator validator,
                                         JsonCodec jsonCodec) {
        this(repository, validator, jsonCodec, Clock.systemUTC());
    }

    ActionErrorMappingRuleService(ActionErrorMappingRuleRepository repository,
                                  ActionErrorMappingRuleValidator validator,
                                  JsonCodec jsonCodec,
                                  Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ActionErrorMappingRuleView> list(ErrorMappingRuleStatus status) {
        List<ActionErrorMappingRuleEntity> entities = status == null
                ? repository.findAllByOrderByProfileIdAscPriorityDescRuleIdAsc()
                : repository.findByStatusOrderByPriorityDescRuleIdAsc(status);
        return entities.stream().map(this::toView).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ActionErrorMappingRuleView get(String ruleId) {
        return toView(required(ruleId));
    }

    @Transactional(readOnly = true)
    public List<ActionErrorMappingRule> activeRules() {
        return repository.findByStatusOrderByPriorityDescRuleIdAsc(ErrorMappingRuleStatus.ACTIVE)
                .stream().map(entity -> entity.rule(jsonCodec)).collect(Collectors.toList());
    }

    @Transactional
    public ActionErrorMappingRuleView create(ActionErrorMappingRule rule) {
        validator.validate(rule);
        if (repository.existsById(rule.ruleId())) {
            throw new ErrorMappingRuleConflictException("异常映射 ruleId 已存在：" + rule.ruleId());
        }
        return toView(repository.save(new ActionErrorMappingRuleEntity(rule, jsonCodec, clock.instant())));
    }

    @Transactional
    public ActionErrorMappingRuleView update(String ruleId,
                                             long expectedRevision,
                                             ActionErrorMappingRule rule) {
        validator.validate(rule);
        if (!ruleId.equals(rule.ruleId())) {
            throw new IllegalArgumentException("路径 ruleId 与规则内容不一致。");
        }
        ActionErrorMappingRuleEntity entity = requiredForUpdate(ruleId);
        assertRevision(entity, expectedRevision);
        if (entity.getStatus() == ErrorMappingRuleStatus.ACTIVE) {
            throw new ErrorMappingRuleConflictException("ACTIVE 映射规则必须先停用后才能修改。");
        }
        entity.update(rule, jsonCodec, clock.instant());
        return toView(repository.save(entity));
    }

    @Transactional
    public ActionErrorMappingRuleView activate(String ruleId, long expectedRevision) {
        ActionErrorMappingRuleEntity entity = requiredForUpdate(ruleId);
        assertRevision(entity, expectedRevision);
        validator.validate(entity.rule(jsonCodec));
        entity.changeStatus(ErrorMappingRuleStatus.ACTIVE, clock.instant());
        return toView(repository.save(entity));
    }

    @Transactional
    public ActionErrorMappingRuleView disable(String ruleId, long expectedRevision) {
        ActionErrorMappingRuleEntity entity = requiredForUpdate(ruleId);
        assertRevision(entity, expectedRevision);
        entity.changeStatus(ErrorMappingRuleStatus.DISABLED, clock.instant());
        return toView(repository.save(entity));
    }

    @Transactional
    public void delete(String ruleId, long expectedRevision) {
        ActionErrorMappingRuleEntity entity = requiredForUpdate(ruleId);
        assertRevision(entity, expectedRevision);
        if (entity.getStatus() == ErrorMappingRuleStatus.ACTIVE) {
            throw new ErrorMappingRuleConflictException("ACTIVE 映射规则必须先停用后才能删除。");
        }
        repository.delete(entity);
    }

    private ActionErrorMappingRuleEntity required(String ruleId) {
        return repository.findById(ruleId)
                .orElseThrow(() -> new ErrorMappingRuleNotFoundException("找不到异常映射规则：" + ruleId));
    }

    private ActionErrorMappingRuleEntity requiredForUpdate(String ruleId) {
        return repository.findByRuleIdForUpdate(ruleId)
                .orElseThrow(() -> new ErrorMappingRuleNotFoundException("找不到异常映射规则：" + ruleId));
    }

    private void assertRevision(ActionErrorMappingRuleEntity entity, long expectedRevision) {
        if (entity.getRevision() != expectedRevision) {
            throw new ErrorMappingRuleConflictException("映射规则已发生变化，请刷新后重试：" + entity.getRuleId());
        }
    }

    private ActionErrorMappingRuleView toView(ActionErrorMappingRuleEntity entity) {
        return new ActionErrorMappingRuleView(entity.getRuleId(), entity.getProfileId(),
                entity.getRevision(), entity.getStatus(), entity.rule(jsonCodec),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
