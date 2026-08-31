package com.kunling.scheduling.action.definition.application;

import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionFailureDirective;
import com.kunling.scheduling.action.definition.domain.ActionFailureDirectiveType;
import com.kunling.scheduling.action.definition.domain.ActionFailurePolicy;
import com.kunling.scheduling.action.definition.domain.ActionFailureRule;
import com.kunling.scheduling.action.definition.domain.ActionStepDefinition;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** 集中校验 Action 通用结构和失败策略；operation 专属参数由下游整包预检。 */
@Component
public class ActionDefinitionValidator {
    private static final Pattern STEP_ID = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,127}");
    private static final Pattern OPERATION = Pattern.compile("[A-Z0-9][A-Z0-9._-]{1,127}");

    public void validateDraft(ActionDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("Action definition 不能为空。");
        requireText(definition.name(), "name");
        if (definition.name().length() > 128) throw new IllegalArgumentException("name 长度不能超过 128。");
        if (definition.timeoutMs() < 1_000 || definition.timeoutMs() > 3_600_000) {
            throw new IllegalArgumentException("timeoutMs 必须在 1000-3600000 之间。");
        }
    }

    public void validateExecutable(ActionDefinition definition) {
        validateDraft(definition);
        if (definition.steps().isEmpty()) throw new IllegalArgumentException("Action 至少需要一个 step。");
        if (definition.steps().size() > 200) throw new IllegalArgumentException("单个 Action 最多允许 200 个 step。");

        Set<String> stepIds = new HashSet<String>();
        for (int index = 0; index < definition.steps().size(); index++) {
            ActionStepDefinition step = definition.steps().get(index);
            validateStep(step, index, stepIds);
        }
    }

    private void validateStep(ActionStepDefinition step,
                              int index,
                              Set<String> stepIds) {
        if (step == null) throw new IllegalArgumentException("steps[" + index + "] 不能为空。");
        requireText(step.stepId(), "steps[" + index + "].stepId");
        if (!STEP_ID.matcher(step.stepId()).matches()) {
            throw new IllegalArgumentException("stepId 格式无效：" + step.stepId());
        }
        if (!stepIds.add(step.stepId())) throw new IllegalArgumentException("stepId 重复：" + step.stepId());
        requireText(step.operation(), "step " + step.stepId() + ".operation");
        if (!OPERATION.matcher(step.operation()).matches()) {
            throw new IllegalArgumentException("operation 必须是稳定的大写标识：" + step.operation());
        }
        if (step.params() == null || !step.params().isObject()) {
            throw new IllegalArgumentException("step " + step.stepId() + " 的 params 必须是 JSON 对象。");
        }
        validateFailurePolicy(step);
    }

    private void validateFailurePolicy(ActionStepDefinition step) {
        ActionFailurePolicy policy = step.onFailure();
        if (policy == null || policy.defaultDirective() == null) {
            throw new IllegalArgumentException("step " + step.stepId() + " 的 onFailure.defaultDirective 不能为空。");
        }
        for (int ruleIndex = 0; ruleIndex < policy.rules().size(); ruleIndex++) {
            ActionFailureRule rule = policy.rules().get(ruleIndex);
            if (rule == null) throw new IllegalArgumentException("step " + step.stepId() + " 包含空失败规则。");
            requireText(rule.reasonCode(), "step " + step.stepId() + ".onFailure.reasonCode");
            validateDirective(step, rule.directive(), "规则 " + (ruleIndex + 1));
        }
        validateDirective(step, policy.defaultDirective(), "default");
    }

    private void validateDirective(ActionStepDefinition step,
                                   ActionFailureDirective directive,
                                   String location) {
        if (directive == null || directive.action() == null) {
            throw new IllegalArgumentException("step " + step.stepId() + " 的 " + location + " 指令不能为空。");
        }
        if (directive.maxRetries() < 0 || directive.maxRetries() > 10) {
            throw new IllegalArgumentException("step " + step.stepId() + " 的 maxRetries 必须在 0-10 之间。");
        }
        if (directive.delayMs() < 0 || directive.delayMs() > 3_600_000) {
            throw new IllegalArgumentException("step " + step.stepId() + " 的 delayMs 必须在 0-3600000 之间。");
        }
        boolean retry = directive.action() == ActionFailureDirectiveType.RETRY_STEP
                || directive.action() == ActionFailureDirectiveType.VERIFY_THEN_RETRY;
        if (retry && directive.maxRetries() == 0) {
            throw new IllegalArgumentException("step " + step.stepId() + " 配置重试时 maxRetries 必须大于 0。");
        }
        if (directive.action() == ActionFailureDirectiveType.VERIFY_THEN_RETRY
                && directive.verifyOperation() == null) {
            throw new IllegalArgumentException("step " + step.stepId()
                    + " 使用 VERIFY_THEN_RETRY 时必须配置 verifyOperation。");
        }
        if (directive.action() == ActionFailureDirectiveType.VERIFY_THEN_RETRY
                && directive.verifyParams() != null && !directive.verifyParams().isObject()) {
            throw new IllegalArgumentException("step " + step.stepId() + " 的 verifyParams 必须是 JSON 对象。");
        }
        if (directive.action() != ActionFailureDirectiveType.VERIFY_THEN_RETRY
                && (directive.verifyOperation() != null || directive.verifyParams() != null)) {
            throw new IllegalArgumentException("step " + step.stepId()
                    + " 只有 VERIFY_THEN_RETRY 可以配置复核操作。");
        }
        if (step.gate() && (directive.action() == ActionFailureDirectiveType.SKIP_STEP
                || directive.onExhaust() == ActionFailureDirectiveType.SKIP_STEP)) {
            throw new IllegalArgumentException("门禁 step " + step.stepId()
                    + " 的任一失败路径都不允许最终 SKIP_STEP。");
        }
        if (retry && directive.onExhaust() == null) {
            throw new IllegalArgumentException("step " + step.stepId() + " 的重试指令必须配置 onExhaust。");
        }
        if (!retry && (directive.maxRetries() != 0 || directive.delayMs() != 0
                || directive.onExhaust() != null)) {
            throw new IllegalArgumentException("step " + step.stepId()
                    + " 的非重试指令不能配置重试参数或 onExhaust。");
        }
        if (directive.onExhaust() != null
                && directive.onExhaust() != ActionFailureDirectiveType.STOP_AND_REPORT
                && directive.onExhaust() != ActionFailureDirectiveType.SKIP_STEP) {
            throw new IllegalArgumentException("onExhaust 只能是 STOP_AND_REPORT 或 SKIP_STEP。");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空。");
        }
    }
}
