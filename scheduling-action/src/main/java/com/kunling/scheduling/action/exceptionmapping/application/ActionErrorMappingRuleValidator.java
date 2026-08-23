package com.kunling.scheduling.action.exceptionmapping.application;

import com.kunling.scheduling.action.exceptionmapping.domain.ActionErrorMappingRule;
import com.kunling.scheduling.action.exceptionmapping.domain.BusinessDisposition;
import com.kunling.scheduling.action.exceptionmapping.domain.DeviceCodeMatchType;
import com.kunling.scheduling.action.exceptionmapping.domain.PackageErrorPolicy;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import com.kunling.scheduling.action.definition.domain.PhaseFailureAction;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** 映射规则保存和启用前的统一校验入口。 */
@Component
public class ActionErrorMappingRuleValidator {
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{1,127}$");
    private static final Pattern BUSINESS_CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$");

    public void validate(ActionErrorMappingRule rule) {
        if (rule == null) throw new IllegalArgumentException("异常映射规则不能为空。");
        requireIdentifier(rule.ruleId(), "ruleId");
        requireIdentifier(rule.profileId(), "profileId");
        if (rule.priority() < 0 || rule.priority() > 100_000) {
            throw new IllegalArgumentException("priority 必须在 0-100000 之间。");
        }
        if (rule.match() == null) throw new IllegalArgumentException("match 不能为空。");
        if (rule.result() == null) throw new IllegalArgumentException("result 不能为空。");
        if (rule.policy() == null) throw new IllegalArgumentException("policy 不能为空。");

        validateMatch(rule);
        validateResult(rule);
        validatePolicy(rule);
    }

    private void validateMatch(ActionErrorMappingRule rule) {
        requireCoreDimension(rule.match().subAction(), "subAction");
        requireCoreDimension(rule.match().vendor(), "vendor");
        requireCoreDimension(rule.match().deviceType(), "deviceType");
        DeviceCodeMatchType matchType = rule.match().matchType();
        String rawCode = rule.match().rawCodePattern();
        if (matchType == DeviceCodeMatchType.EXACT && "*".equals(rawCode)) {
            throw new IllegalArgumentException("EXACT 规则必须配置明确的厂家原始码。");
        }
        if (matchType == DeviceCodeMatchType.RANGE) {
            String[] bounds = rawCode.split("-", -1);
            if (bounds.length != 2) throw new IllegalArgumentException("RANGE 原始码必须使用 下限-上限 格式。");
            try {
                long lower = Long.parseLong(bounds[0].trim());
                long upper = Long.parseLong(bounds[1].trim());
                if (lower > upper) throw new IllegalArgumentException("RANGE 范围下限不能大于上限。");
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("RANGE 原始码上下限必须是整数。", exception);
            }
        }
        if (matchType == DeviceCodeMatchType.PATTERN && "*".equals(rawCode)) {
            throw new IllegalArgumentException("PATTERN 规则不能使用全量星号，请使用 FALLBACK。");
        }
        if (matchType == DeviceCodeMatchType.FALLBACK && !"*".equals(rawCode)) {
            throw new IllegalArgumentException("FALLBACK 的厂家原始码必须为星号。");
        }
    }

    private void validateResult(ActionErrorMappingRule rule) {
        requireBusinessCode(rule.result().businessCode(), "businessCode");
        if (rule.result().businessMessage() == null) {
            throw new IllegalArgumentException("businessMessage 不能为空。");
        }
        requireBusinessCode(rule.result().reasonCode(), "reasonCode");
        if (rule.result().businessDisposition() == null) {
            throw new IllegalArgumentException("businessDisposition 不能为空。");
        }
        if (rule.result().physicalOutcome() == null) {
            throw new IllegalArgumentException("physicalOutcome 不能为空。");
        }
    }

    private void validatePolicy(ActionErrorMappingRule rule) {
        PackageErrorPolicy policy = rule.policy();
        if (policy.maxRetries() < 0 || policy.maxRetries() > 10) {
            throw new IllegalArgumentException("maxRetries 必须在 0-10 之间。");
        }
        if (policy.retryDelayMs() < 0 || policy.retryDelayMs() > 3_600_000) {
            throw new IllegalArgumentException("retryDelayMs 必须在 0-3600000 之间。");
        }
        boolean retry = policy.failureStrategy() == PhaseFailureAction.RETRY_PHASE
                || policy.failureStrategy() == PhaseFailureAction.VERIFY_BEFORE_RETRY;
        if (retry && policy.maxRetries() == 0) {
            throw new IllegalArgumentException("重试策略的 maxRetries 必须大于 0。");
        }
        if (policy.failureStrategy() == PhaseFailureAction.VERIFY_BEFORE_RETRY
                && policy.verifyCapability() == null) {
            throw new IllegalArgumentException("VERIFY_BEFORE_RETRY 必须配置 verifyCapability。");
        }
        if (policy.failureStrategy() == PhaseFailureAction.SKIP
                && rule.result().physicalOutcome() == PhysicalOutcome.UNKNOWN) {
            throw new IllegalArgumentException("物理结果未知的异常禁止配置 SKIP。");
        }
        if (rule.result().businessDisposition() == BusinessDisposition.CRITICAL
                && policy.failureStrategy() != PhaseFailureAction.ABORT) {
            throw new IllegalArgumentException("CRITICAL 异常必须配置 ABORT。");
        }
    }

    private void requireIdentifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " 格式无效，仅允许字母、数字、点、下划线和短横线。");
        }
    }

    private void requireBusinessCode(String value, String field) {
        if (value == null || !BUSINESS_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " 格式无效。");
        }
    }

    private void requireCoreDimension(String value, String field) {
        if (value == null || value.trim().isEmpty() || value.contains("*")) {
            throw new IllegalArgumentException(field + " 必须配置明确值，不能使用星号。");
        }
    }
}
