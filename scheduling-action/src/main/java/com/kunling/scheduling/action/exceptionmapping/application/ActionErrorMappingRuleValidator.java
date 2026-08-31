package com.kunling.scheduling.action.exceptionmapping.application;

import com.kunling.scheduling.action.exceptionmapping.domain.ActionErrorMappingRule;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** 映射规则保存和启用前的统一校验入口。 */
@Component
public class ActionErrorMappingRuleValidator {
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{1,127}$");
    private static final Pattern BUSINESS_CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$");
    private static final Pattern OPERATION = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{1,127}$");
    private static final Pattern EXTENSIBLE_IDENTIFIER = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,127}$");

    public void validate(ActionErrorMappingRule rule) {
        if (rule == null) throw new IllegalArgumentException("异常映射规则不能为空。");
        requireIdentifier(rule.ruleId(), "ruleId");
        requireIdentifier(rule.profileId(), "profileId");
        if (rule.priority() < 0 || rule.priority() > 100_000) {
            throw new IllegalArgumentException("priority 必须在 0-100000 之间。");
        }
        if (rule.match() == null) throw new IllegalArgumentException("match 不能为空。");
        if (rule.result() == null) throw new IllegalArgumentException("result 不能为空。");

        if (rule.match().operation() != null && !OPERATION.matcher(rule.match().operation()).matches()) {
            throw new IllegalArgumentException("operation 必须是稳定的大写标识。");
        }
        requireExtensibleIdentifier(rule.match().vendor(), "vendor");
        requireExtensibleIdentifier(rule.match().deviceType(), "deviceType");
        requireText(rule.match().rawCode(), "rawCode");

        requireBusinessCode(rule.result().businessCode(), "businessCode");
        requireText(rule.result().businessMessage(), "businessMessage");
        requireBusinessCode(rule.result().reasonCode(), "reasonCode");
        if (rule.result().handlingConstraint() == null) {
            throw new IllegalArgumentException("handlingConstraint 不能为空。");
        }
    }

    private void requireIdentifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " 格式无效。");
        }
    }

    private void requireBusinessCode(String value, String field) {
        if (value == null || !BUSINESS_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " 格式无效。");
        }
    }

    private void requireExtensibleIdentifier(String value, String field) {
        if (value == null || !EXTENSIBLE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " 必须是稳定的大写标识。");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空。");
        }
    }
}
