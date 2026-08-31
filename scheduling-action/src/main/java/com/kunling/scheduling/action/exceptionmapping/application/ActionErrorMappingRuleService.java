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

/** 映射规则管理的唯一入口；只有启用规则参与组包和最终异常解释。 */
@Service
public class ActionErrorMappingRuleService {
    private final ActionErrorMappingRuleRepository repository;
    private final ActionErrorMappingRuleValidator validator;
    private final BusinessErrorMappingEngine mappingEngine;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    @Autowired
    public ActionErrorMappingRuleService(ActionErrorMappingRuleRepository repository,
                                         ActionErrorMappingRuleValidator validator,
                                         BusinessErrorMappingEngine mappingEngine,
                                         JsonCodec jsonCodec) {
        this(repository, validator, mappingEngine, jsonCodec, Clock.systemUTC());
    }

    ActionErrorMappingRuleService(ActionErrorMappingRuleRepository repository,
                                  ActionErrorMappingRuleValidator validator,
                                  BusinessErrorMappingEngine mappingEngine,
                                  JsonCodec jsonCodec,
                                  Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.mappingEngine = mappingEngine;
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

    /** 使用当前启用规则预览一次精确匹配；该结果不会改变任何配置。 */
    @Transactional(readOnly = true)
    public BusinessErrorDecision preview(ErrorMappingContext context) {
        if (context == null || context.vendor() == null || context.deviceType() == null
                || context.rawCode() == null) {
            throw new IllegalArgumentException("预览必须提供 vendor、deviceType 和 rawCode。");
        }
        return mappingEngine.resolve(activeRules(), context);
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
        ActionErrorMappingRule candidate = entity.rule(jsonCodec);
        validator.validate(candidate);
        rejectConflictingActiveRule(candidate);
        entity.changeStatus(ErrorMappingRuleStatus.ACTIVE, clock.instant());
        return toView(repository.save(entity));
    }

    private void rejectConflictingActiveRule(ActionErrorMappingRule candidate) {
        for (ActionErrorMappingRule active : activeRules()) {
            if (active.ruleId().equals(candidate.ruleId()) || !keysOverlap(active, candidate)) continue;
            if (!sameResult(active, candidate)) {
                throw new ErrorMappingRuleConflictException("异常映射核心键与已启用规则冲突："
                        + active.ruleId());
            }
        }
    }

    private boolean keysOverlap(ActionErrorMappingRule left, ActionErrorMappingRule right) {
        return same(left.match().vendor(), right.match().vendor())
                && same(left.match().deviceType(), right.match().deviceType())
                && exact(left.match().rawCode(), right.match().rawCode())
                && (left.match().operation() == null || right.match().operation() == null
                || same(left.match().operation(), right.match().operation()));
    }

    private boolean sameResult(ActionErrorMappingRule left, ActionErrorMappingRule right) {
        return exact(left.result().businessCode(), right.result().businessCode())
                && exact(left.result().businessMessage(), right.result().businessMessage())
                && exact(left.result().reasonCode(), right.result().reasonCode())
                && left.result().handlingConstraint() == right.result().handlingConstraint()
                && exact(left.result().handlingAdvice(), right.result().handlingAdvice());
    }

    private boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private boolean exact(String left, String right) {
        return left == null ? right == null : left.equals(right);
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
