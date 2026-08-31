package com.kunling.scheduling.action.exceptionmapping.application;

import com.kunling.scheduling.action.exceptionmapping.domain.ActionErrorMappingRule;
import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleMatch;
import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleResult;
import com.kunling.scheduling.action.exceptionmapping.domain.HandlingConstraint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 按当前已启用规则执行厂家原始码精确映射。 */
@Component
public class BusinessErrorMappingEngine {

    public BusinessErrorDecision resolve(List<ActionErrorMappingRule> sourceRules,
                                         ErrorMappingContext context) {
        if (context == null) throw new IllegalArgumentException("异常映射上下文不能为空。");
        for (ActionErrorMappingRule rule : ordered(sourceRules)) {
            if (matches(rule.match(), context)) return decision(rule);
        }
        return new BusinessErrorDecision("5999", "未映射设备异常",
                "DEVICE.UNMAPPED_FAULT", HandlingConstraint.MANUAL_INTERVENTION,
                "GLOBAL-FALLBACK", "GLOBAL", "保留厂家原始异常并补充映射规则");
    }

    private List<ActionErrorMappingRule> ordered(List<ActionErrorMappingRule> source) {
        List<ActionErrorMappingRule> rules = source == null
                ? new ArrayList<ActionErrorMappingRule>() : new ArrayList<ActionErrorMappingRule>(source);
        rules.removeIf(rule -> rule == null || rule.match() == null || rule.result() == null);
        rules.sort(Comparator.comparingInt(ActionErrorMappingRule::priority).reversed()
                .thenComparing(rule -> rule.ruleId() == null ? "" : rule.ruleId()));
        return rules;
    }

    private boolean matches(ErrorMappingRuleMatch match, ErrorMappingContext context) {
        return (match.operation() == null || equalsExact(match.operation(), context.operation()))
                && equalsExact(match.vendor(), context.vendor())
                && equalsExact(match.deviceType(), context.deviceType())
                && equalsExact(match.rawCode(), context.rawCode());
    }

    private BusinessErrorDecision decision(ActionErrorMappingRule rule) {
        ErrorMappingRuleResult result = rule.result();
        return new BusinessErrorDecision(result.businessCode(), result.businessMessage(),
                result.reasonCode(), result.handlingConstraint(), rule.ruleId(),
                rule.profileId(), result.handlingAdvice());
    }

    private boolean equalsExact(String left, String right) {
        return left != null && left.equals(right);
    }
}
