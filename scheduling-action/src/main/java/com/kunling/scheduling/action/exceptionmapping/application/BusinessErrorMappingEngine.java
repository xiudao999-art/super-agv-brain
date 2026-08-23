package com.kunling.scheduling.action.exceptionmapping.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.exceptionmapping.domain.BusinessDisposition;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 对执行快照中的映射规则做确定性匹配。
 *
 * <p>该模块不查询当前配置表，确保运行期间即使配置发生变化，本次执行结果仍按原快照解释。</p>
 */
@Component
public class BusinessErrorMappingEngine {

    public BusinessErrorDecision resolve(JsonNode errorPolicySnapshot, ErrorMappingContext context) {
        if (context == null) throw new IllegalArgumentException("异常映射上下文不能为空。");
        JsonNode rules = errorPolicySnapshot == null ? null : errorPolicySnapshot.path("rules");
        if (rules != null && rules.isArray()) {
            List<JsonNode> orderedRules = new ArrayList<JsonNode>();
            rules.forEach(orderedRules::add);
            orderedRules.sort(Comparator
                    .comparingInt((JsonNode rule) -> rule.path("priority").asInt(0)).reversed()
                    .thenComparing(rule -> defaultString(text(rule, "ruleId"))));
            for (JsonNode rule : orderedRules) {
                if (matches(rule, context)) return decision(rule, context);
            }
        }
        return fallback(errorPolicySnapshot, context);
    }

    private boolean matches(JsonNode rule, ErrorMappingContext context) {
        JsonNode match = rule.path("match");
        if (!matchesSubAction(rule, match, context.subAction())) return false;
        if (!matchesPattern(text(match, "vendor"), context.vendor())) return false;
        if (!matchesPattern(text(match, "deviceType"), context.deviceType())) return false;
        String matchType = upper(text(match, "matchType"));
        String expectedCode = text(match, "rawCodePattern");
        if ("EXACT".equals(matchType)) return equalsIgnoreCase(expectedCode, context.deviceCode());
        if ("RANGE".equals(matchType)) return inNumericRange(expectedCode, context.deviceCode());
        if ("PATTERN".equals(matchType)) return matchesPattern(expectedCode, context.deviceCode());
        return "FALLBACK".equals(matchType);
    }

    private boolean matchesSubAction(JsonNode rule, JsonNode match, String actualSubAction) {
        String configuredSubAction = text(match, "subAction");
        if (configuredSubAction != null) {
            return matchesPattern(configuredSubAction, actualSubAction);
        }
        // 兼容此前已经冻结的快照：旧结构把子动作保存在适用 phase 或规则顶层。
        JsonNode applicablePhases = rule.path("applicablePhases");
        if (applicablePhases.isArray()) {
            for (JsonNode phase : applicablePhases) {
                if (matchesPattern(text(phase, "subAction"), actualSubAction)) return true;
            }
            return false;
        }
        return matchesPattern(text(rule, "subAction"), actualSubAction);
    }

    private BusinessErrorDecision decision(JsonNode rule, ErrorMappingContext context) {
        JsonNode result = rule.path("result");
        BusinessDisposition disposition = enumValue(BusinessDisposition.class,
                text(result, "businessDisposition"), BusinessDisposition.MANUAL_INTERVENTION);
        PhysicalOutcome configuredOutcome = enumValue(PhysicalOutcome.class,
                text(result, "physicalOutcome"), PhysicalOutcome.UNKNOWN);
        PhysicalOutcome outcome = context.physicalResultKnown()
                ? configuredOutcome : PhysicalOutcome.UNKNOWN;
        if ((outcome == PhysicalOutcome.UNKNOWN || outcome == PhysicalOutcome.PARTIALLY_COMPLETED)
                && disposition == BusinessDisposition.RETRYABLE) {
            disposition = BusinessDisposition.MANUAL_INTERVENTION;
        }
        return new BusinessErrorDecision(text(result, "businessCode"),
                text(result, "businessMessage"), text(result, "reasonCode"),
                disposition, outcome, text(rule, "ruleId"), text(rule, "profileId"),
                text(result, "handlingAdvice"));
    }

    private BusinessErrorDecision fallback(JsonNode snapshot, ErrorMappingContext context) {
        JsonNode result = snapshot == null ? null : snapshot.path("fallback").path("result");
        BusinessDisposition disposition = enumValue(BusinessDisposition.class,
                text(result, "businessDisposition"), BusinessDisposition.MANUAL_INTERVENTION);
        PhysicalOutcome configuredOutcome = enumValue(PhysicalOutcome.class,
                text(result, "physicalOutcome"), PhysicalOutcome.UNKNOWN);
        PhysicalOutcome outcome = context.physicalResultKnown()
                ? configuredOutcome : PhysicalOutcome.UNKNOWN;
        if ((outcome == PhysicalOutcome.UNKNOWN || outcome == PhysicalOutcome.PARTIALLY_COMPLETED)
                && disposition == BusinessDisposition.RETRYABLE) {
            disposition = BusinessDisposition.MANUAL_INTERVENTION;
        }
        return new BusinessErrorDecision(
                defaultString(text(result, "businessCode"), "5999"),
                defaultString(text(result, "businessMessage"), "未映射设备异常"),
                defaultString(text(result, "reasonCode"), "DEVICE.UNMAPPED_FAULT"),
                disposition, outcome, "GLOBAL-FALLBACK", "GLOBAL",
                defaultString(text(result, "handlingAdvice"), "保留厂家原始异常并补充映射规则"));
    }

    private boolean matchesPattern(String expected, String actual) {
        if (expected == null || "*".equals(expected)) return true;
        if (actual == null) return false;
        String normalizedExpected = upper(expected);
        String normalizedActual = upper(actual);
        int expectedIndex = 0;
        int actualIndex = 0;
        int starIndex = -1;
        int retryActualIndex = -1;
        while (actualIndex < normalizedActual.length()) {
            if (expectedIndex < normalizedExpected.length()
                    && normalizedExpected.charAt(expectedIndex) == normalizedActual.charAt(actualIndex)) {
                expectedIndex++;
                actualIndex++;
            } else if (expectedIndex < normalizedExpected.length()
                    && normalizedExpected.charAt(expectedIndex) == '*') {
                starIndex = expectedIndex++;
                retryActualIndex = actualIndex;
            } else if (starIndex >= 0) {
                expectedIndex = starIndex + 1;
                actualIndex = ++retryActualIndex;
            } else {
                return false;
            }
        }
        while (expectedIndex < normalizedExpected.length()
                && normalizedExpected.charAt(expectedIndex) == '*') {
            expectedIndex++;
        }
        return expectedIndex == normalizedExpected.length();
    }

    private boolean inNumericRange(String range, String actual) {
        if (range == null || actual == null) return false;
        String[] bounds = range.split("-", -1);
        if (bounds.length != 2) return false;
        try {
            long lower = Long.parseLong(bounds[0].trim());
            long upper = Long.parseLong(bounds[1].trim());
            long value = Long.parseLong(actual.trim());
            return lower <= value && value <= upper;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, upper(value));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
