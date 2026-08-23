package com.kunling.scheduling.action.exceptionmapping.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionPhaseDefinition;
import com.kunling.scheduling.action.definition.domain.DownstreamSubAction;
import com.kunling.scheduling.action.definition.domain.PhaseFailureAction;
import com.kunling.scheduling.action.definition.domain.RetryExhaustedAction;
import com.kunling.scheduling.action.exceptionmapping.domain.ActionErrorMappingRule;
import com.kunling.scheduling.action.exceptionmapping.domain.BusinessDisposition;
import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleMatch;
import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleResult;
import com.kunling.scheduling.action.exceptionmapping.domain.PackageErrorPolicy;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 将当前有效映射规则编译为本次 Action 的只读异常策略快照。 */
@Component
public class ErrorPolicySnapshotCompiler {
    private static final String SCHEMA_VERSION = "1.0";
    private final ObjectMapper objectMapper;
    private final JsonCodec jsonCodec;

    public ErrorPolicySnapshotCompiler(ObjectMapper objectMapper, JsonCodec jsonCodec) {
        this.objectMapper = objectMapper;
        this.jsonCodec = jsonCodec;
    }

    public ObjectNode compile(ActionDefinition definition, List<ActionErrorMappingRule> sourceRules) {
        if (definition == null) throw new IllegalArgumentException("Action 定义不能为空。");
        List<ActionErrorMappingRule> rules = sourceRules == null
                ? new ArrayList<ActionErrorMappingRule>() : new ArrayList<ActionErrorMappingRule>(sourceRules);
        rules.sort(Comparator.comparingInt(ActionErrorMappingRule::priority).reversed()
                .thenComparing(rule -> rule.ruleId() == null ? "" : rule.ruleId()));

        ArrayNode compiledRules = objectMapper.createArrayNode();
        for (ActionErrorMappingRule rule : rules) {
            if (rule == null || rule.match() == null) {
                continue;
            }
            ArrayNode applicablePhases = objectMapper.createArrayNode();
            for (ActionPhaseDefinition phase : definition.phases()) {
                if (!phase.enabled()
                        || !matchesPattern(rule.match().subAction(), phase.subAction().wireName())) {
                    continue;
                }
                validateForPhase(rule, phase);
                ObjectNode applicablePhase = objectMapper.createObjectNode();
                applicablePhase.put("phaseId", phase.phaseId());
                applicablePhase.put("subAction", phase.subAction().wireName());
                applicablePhases.add(applicablePhase);
            }
            // 同一厂家码规则只编码一次，重复 phase 只追加轻量作用域，避免动作包按节点倍增。
            if (!applicablePhases.isEmpty()) {
                compiledRules.add(encode(rule, definition.actionKey(), applicablePhases));
            }
        }

        ObjectNode content = objectMapper.createObjectNode();
        content.set("evaluation", evaluation());
        content.set("rules", compiledRules);
        content.set("fallback", fallback());

        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("schemaVersion", SCHEMA_VERSION);
        snapshot.put("configHash", jsonCodec.sha256(jsonCodec.writeCanonical(content)));
        snapshot.set("evaluation", content.path("evaluation"));
        snapshot.set("rules", compiledRules);
        snapshot.set("fallback", content.path("fallback"));
        return snapshot;
    }

    private ObjectNode evaluation() {
        ObjectNode evaluation = objectMapper.createObjectNode();
        evaluation.put("selection", "FIRST_MATCH");
        evaluation.put("order", "PRIORITY_DESC_RULE_ID_ASC");
        evaluation.put("unmatched", "USE_FALLBACK");
        evaluation.put("matchedRuleOverridesPhasePolicy", true);
        return evaluation;
    }

    private ObjectNode encode(ActionErrorMappingRule rule,
                              String actionKey,
                              ArrayNode applicablePhases) {
        ObjectNode encoded = objectMapper.createObjectNode();
        encoded.put("ruleId", rule.ruleId());
        encoded.put("profileId", rule.profileId());
        encoded.put("priority", rule.priority());
        encoded.put("actionKey", actionKey);
        encoded.set("applicablePhases", applicablePhases);
        encoded.set("match", encodeMatch(rule.match()));
        encoded.set("result", encodeResult(rule.result()));
        encoded.set("policy", encodePolicy(rule.policy()));
        return encoded;
    }

    private ObjectNode encodeMatch(ErrorMappingRuleMatch match) {
        ObjectNode encoded = objectMapper.createObjectNode();
        encoded.put("subAction", match.subAction());
        encoded.put("vendor", match.vendor());
        encoded.put("deviceType", match.deviceType());
        encoded.put("matchType", match.matchType().name());
        encoded.put("rawCodePattern", match.rawCodePattern());
        return encoded;
    }

    private ObjectNode encodeResult(ErrorMappingRuleResult result) {
        if (result == null) throw new IllegalArgumentException("异常映射结果不能为空。");
        ObjectNode encoded = objectMapper.createObjectNode();
        encoded.put("businessCode", result.businessCode());
        encoded.put("businessMessage", result.businessMessage());
        encoded.put("reasonCode", result.reasonCode());
        encoded.put("businessDisposition", result.businessDisposition().name());
        encoded.put("physicalOutcome", result.physicalOutcome().name());
        if (result.handlingAdvice() != null) encoded.put("handlingAdvice", result.handlingAdvice());
        return encoded;
    }

    private ObjectNode encodePolicy(PackageErrorPolicy policy) {
        PackageErrorPolicy actual = policy == null
                ? new PackageErrorPolicy(PhaseFailureAction.ABORT, 0, 0, null,
                RetryExhaustedAction.HOLD, BusinessDisposition.MANUAL_INTERVENTION) : policy;
        ObjectNode encoded = objectMapper.createObjectNode();
        encoded.put("failureStrategy", actual.failureStrategy().name());
        encoded.put("maxRetries", actual.maxRetries());
        encoded.put("retryDelayMs", actual.retryDelayMs());
        if (actual.verifyCapability() != null) encoded.put("verifyCapability", actual.verifyCapability());
        encoded.put("onExhaust", actual.onExhaust().name());
        encoded.put("onExhaustDisposition", actual.onExhaustDisposition().name());
        return encoded;
    }

    private ObjectNode fallback() {
        ObjectNode fallback = objectMapper.createObjectNode();
        fallback.set("result", encodeResult(new ErrorMappingRuleResult("5999", "未映射设备异常",
                "DEVICE.UNMAPPED_FAULT",
                BusinessDisposition.MANUAL_INTERVENTION, PhysicalOutcome.UNKNOWN,
                "保留厂家原始异常并补充映射规则")));
        fallback.set("policy", encodePolicy(new PackageErrorPolicy(PhaseFailureAction.ABORT,
                0, 0, null, RetryExhaustedAction.HOLD,
                BusinessDisposition.MANUAL_INTERVENTION)));
        return fallback;
    }

    private void validateForPhase(ActionErrorMappingRule rule, ActionPhaseDefinition phase) {
        if (rule.policy() == null) return;
        PackageErrorPolicy policy = rule.policy();
        if (policy.failureStrategy() == PhaseFailureAction.SKIP && phase.gate()) {
            throw new IllegalArgumentException("闸门 phase " + phase.phaseId() + " 不允许异常策略 SKIP。");
        }
        if (policy.maxRetries() < 0 || policy.maxRetries() > 10) {
            throw new IllegalArgumentException("异常映射规则 " + rule.ruleId() + " 的 maxRetries 必须在 0-10 之间。");
        }
        if (policy.retryDelayMs() < 0 || policy.retryDelayMs() > 3_600_000) {
            throw new IllegalArgumentException("异常映射规则 " + rule.ruleId()
                    + " 的 retryDelayMs 必须在 0-3600000 之间。");
        }
        boolean retry = policy.failureStrategy() == PhaseFailureAction.RETRY_PHASE
                || policy.failureStrategy() == PhaseFailureAction.VERIFY_BEFORE_RETRY;
        if (retry && policy.maxRetries() == 0) {
            throw new IllegalArgumentException("异常映射规则 " + rule.ruleId() + " 配置重试时 maxRetries 必须大于 0。");
        }
        if (policy.failureStrategy() == PhaseFailureAction.VERIFY_BEFORE_RETRY
                && policy.verifyCapability() == null) {
            throw new IllegalArgumentException("异常映射规则 " + rule.ruleId()
                    + " 使用 VERIFY_BEFORE_RETRY 时必须配置 verifyCapability。");
        }
        if (policy.failureStrategy() == PhaseFailureAction.RETRY_PHASE
                && hasPhysicalSideEffect(phase.subAction())) {
            throw new IllegalArgumentException("存在物理副作用的 phase " + phase.phaseId()
                    + " 只能使用 VERIFY_BEFORE_RETRY，禁止直接 RETRY_PHASE。");
        }
    }

    private boolean hasPhysicalSideEffect(DownstreamSubAction subAction) {
        return subAction == DownstreamSubAction.MOVE_TO_MAP_POINT
                || subAction == DownstreamSubAction.MOVE_TO_POSE
                || subAction == DownstreamSubAction.GRIP_OPEN
                || subAction == DownstreamSubAction.GRIP_CLOSE;
    }

    private boolean matchesPattern(String pattern, String actual) {
        if (pattern == null || "*".equals(pattern)) return true;
        if (actual == null) return false;
        String expected = pattern.toUpperCase(Locale.ROOT);
        String value = actual.toUpperCase(Locale.ROOT);
        int expectedIndex = 0;
        int actualIndex = 0;
        int starIndex = -1;
        int retryIndex = -1;
        while (actualIndex < value.length()) {
            if (expectedIndex < expected.length() && expected.charAt(expectedIndex) == value.charAt(actualIndex)) {
                expectedIndex++;
                actualIndex++;
            } else if (expectedIndex < expected.length() && expected.charAt(expectedIndex) == '*') {
                starIndex = expectedIndex++;
                retryIndex = actualIndex;
            } else if (starIndex >= 0) {
                expectedIndex = starIndex + 1;
                actualIndex = ++retryIndex;
            } else {
                return false;
            }
        }
        while (expectedIndex < expected.length() && expected.charAt(expectedIndex) == '*') expectedIndex++;
        return expectedIndex == expected.length();
    }
}
