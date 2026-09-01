package com.kunling.scheduling.action.robotbridge.infrastructure.compat.cnet8;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.robotbridge.application.RobotActionCommand;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 把 Action 2.0 的规范执行包渲染成 cnet8 当前能够直接反序列化的 ExecutionPlan。
 *
 * <p>本类只做线协议结构转换；原子操作参数保持为不透明 JSON，不在 Action 中复制设备 SDK
 * 的单位换算和命令模型。</p>
 */
@Component
public class Cnet8ExecutionPlanRenderer {
    private static final int MAX_RETRIES = 3;
    private static final int MAX_BACKOFF_MS = 300_000;
    private static final boolean CNET8_FORCE_DEBUG_AFTER_OPERATOR_CONFIRMATION = false;

    private final ObjectMapper objectMapper;
    private final JsonCodec jsonCodec;

    public Cnet8ExecutionPlanRenderer(ObjectMapper objectMapper, JsonCodec jsonCodec) {
        this.objectMapper = objectMapper;
        this.jsonCodec = jsonCodec;
    }

    public ObjectNode render(RobotActionCommand command) {
        if (command == null) throw new IllegalArgumentException("Action COMMAND 不能为空");
        JsonNode sourceSteps = command.input().at("/executionPlan/steps");
        if (!sourceSteps.isArray() || sourceSteps.size() == 0) {
            throw new IllegalArgumentException("input.executionPlan.steps 必须是非空数组");
        }

        ArrayNode renderedSteps = objectMapper.createArrayNode();
        for (JsonNode sourceStep : sourceSteps) {
            renderedSteps.add(renderStep(sourceStep));
        }
        validateRetryBudget(renderedSteps, command.timeoutMs());

        ObjectNode plan = objectMapper.createObjectNode();
        plan.put("ActionInstanceId", command.actionInstanceId());
        plan.put("DeviceCommandId", command.deviceCommandId());
        plan.put("WorkflowInstanceId", "");
        plan.put("WorkflowNodeInstanceId", "");
        plan.put("PackageHash", computeTransportHash(command, renderedSteps));
        plan.put("TimeoutMs", command.timeoutMs());
        plan.set("Steps", renderedSteps);
        return plan;
    }

    private ObjectNode renderStep(JsonNode sourceStep) {
        String stepId = requiredText(sourceStep, "stepId");
        String operation = requiredText(sourceStep, "operation");
        JsonNode params = sourceStep.get("params");
        if (params == null || !params.isObject()) {
            throw new IllegalArgumentException(stepId + ".params 必须是对象");
        }
        JsonNode onFailure = sourceStep.path("onFailure");
        if (!onFailure.isObject() || !onFailure.path("rules").isArray()
                || !onFailure.path("default").isObject()) {
            throw new IllegalArgumentException(stepId + ".onFailure 必须包含 rules 和 default");
        }

        ArrayNode failureRules = objectMapper.createArrayNode();
        for (JsonNode sourceRule : onFailure.path("rules")) {
            failureRules.add(renderExplicitRule(stepId, operation, sourceRule));
        }
        // cnet8 要求 OnFailure 非空且没有独立 default 字段，因此把默认策略编译成全通配规则。
        failureRules.add(renderFailureRule(stepId, "*", "*", "*", "*", onFailure.path("default")));
        validateUniqueSelectors(stepId, failureRules);

        ObjectNode rendered = objectMapper.createObjectNode();
        rendered.put("StepId", stepId);
        rendered.put("Operation", operation);
        rendered.set("Parameters", params.deepCopy());
        rendered.put("Gate", requiredBoolean(sourceStep, "gate"));
        // cnet8 当前会把该本地调试属性参与 ExecutionPlanHash；生产 Action 永远不得开启它。
        rendered.put("ForceDebugAfterOperatorConfirmation",
                CNET8_FORCE_DEBUG_AFTER_OPERATOR_CONFIRMATION);
        rendered.set("OnFailure", failureRules);
        return rendered;
    }

    private void validateUniqueSelectors(String stepId, ArrayNode failureRules) {
        Set<String> selectors = new LinkedHashSet<String>();
        for (JsonNode rule : failureRules) {
            String selector = (rule.path("Vendor").asText() + "\u001f"
                    + rule.path("DeviceType").asText() + "\u001f"
                    + rule.path("RawCode").asText() + "\u001f"
                    + rule.path("Operation").asText()).toUpperCase(Locale.ROOT);
            if (!selectors.add(selector)) {
                throw new RobotUnavailableException(stepId + " 包含 cnet8 无法区分的重复失败规则");
            }
        }
    }

    private void validateRetryBudget(ArrayNode renderedSteps, int timeoutMs) {
        long backoffBudgetMs = 0L;
        for (JsonNode step : renderedSteps) {
            for (JsonNode rule : step.path("OnFailure")) {
                backoffBudgetMs += (long) rule.path("MaxRetries").asInt(0)
                        * rule.path("BackoffMs").asInt(0);
            }
        }
        if (backoffBudgetMs >= timeoutMs) {
            throw new RobotUnavailableException("cnet8 最坏重试退避预算 " + backoffBudgetMs
                    + "ms 不得达到或超过 timeoutMs=" + timeoutMs + "ms");
        }
    }

    private ObjectNode renderExplicitRule(String stepId, String operation, JsonNode sourceRule) {
        JsonNode when = sourceRule.path("when");
        String source = requiredText(when, "source");
        if (!"DEVICE".equals(source)) {
            throw new RobotUnavailableException("cnet8 当前不能等价执行 CLIENT 失败规则："
                    + requiredText(sourceRule, "policyId"));
        }
        return renderFailureRule(stepId,
                requiredText(when, "vendor"),
                requiredText(when, "deviceType"),
                requiredText(when, "code"),
                operation,
                sourceRule.path("then"));
    }

    private ObjectNode renderFailureRule(String stepId,
                                         String vendor,
                                         String deviceType,
                                         String rawCode,
                                         String operation,
                                         JsonNode directive) {
        String action = requiredText(directive, "action");
        boolean retry = "RETRY_STEP".equals(action) || "VERIFY_THEN_RETRY".equals(action);
        boolean supported = retry || "SKIP_STEP".equals(action) || "STOP_AND_REPORT".equals(action);
        if (!supported) {
            throw new RobotUnavailableException("cnet8 不支持失败指令：" + action);
        }

        int maxRetries = directive.path("maxRetries").asInt(0);
        int backoffMs = directive.path("delayMs").asInt(0);
        if (retry) {
            if (maxRetries < 1 || maxRetries > MAX_RETRIES) {
                throw new RobotUnavailableException(stepId + " 的 cnet8 重试次数必须在 1-"
                        + MAX_RETRIES + " 之间");
            }
            String onExhaust = requiredText(directive, "onExhaust");
            if (!"STOP_AND_REPORT".equals(onExhaust)) {
                throw new RobotUnavailableException(stepId
                        + " 的 cnet8 重试耗尽后只能 STOP_AND_REPORT，不能等价执行 " + onExhaust);
            }
        } else if (maxRetries != 0 || backoffMs != 0) {
            throw new IllegalArgumentException(stepId + " 的非重试指令不能携带重试参数");
        }
        if (backoffMs < 0 || backoffMs > MAX_BACKOFF_MS) {
            throw new RobotUnavailableException(stepId + " 的 cnet8 退避时间必须在 0-"
                    + MAX_BACKOFF_MS + "ms 之间");
        }

        JsonNode verification = null;
        if ("VERIFY_THEN_RETRY".equals(action)) {
            JsonNode verify = directive.path("verify");
            if (!verify.isObject() || !verify.path("params").isObject()) {
                throw new IllegalArgumentException(stepId + " 的 VERIFY_THEN_RETRY 缺少复核操作参数");
            }
            ObjectNode encoded = objectMapper.createObjectNode();
            encoded.put("Operation", requiredText(verify, "operation"));
            encoded.set("Parameters", verify.path("params").deepCopy());
            verification = encoded;
        }

        ObjectNode rendered = objectMapper.createObjectNode();
        rendered.put("Vendor", vendor);
        rendered.put("DeviceType", deviceType);
        rendered.put("RawCode", rawCode);
        rendered.put("Operation", operation);
        rendered.put("Directive", action);
        rendered.put("MaxRetries", maxRetries);
        rendered.put("BackoffMs", backoffMs);
        if (verification == null) rendered.putNull("Verification");
        else rendered.set("Verification", verification);
        return rendered;
    }

    /** 按 cnet8 的 ExecutionPlanHash.Compute 字段集合生成传输哈希，不改变 Action packageHash。 */
    private String computeTransportHash(RobotActionCommand command, ArrayNode renderedSteps) {
        ObjectNode hashSource = objectMapper.createObjectNode();
        hashSource.put("actionInstanceId", command.actionInstanceId());
        hashSource.put("deviceCommandId", command.deviceCommandId());
        hashSource.put("workflowInstanceId", "");
        hashSource.put("workflowNodeInstanceId", "");
        hashSource.put("timeoutMs", command.timeoutMs());

        ArrayNode hashSteps = objectMapper.createArrayNode();
        for (JsonNode renderedStep : renderedSteps) {
            ObjectNode step = objectMapper.createObjectNode();
            step.set("stepId", renderedStep.path("StepId").deepCopy());
            step.set("operation", renderedStep.path("Operation").deepCopy());
            step.set("parameters", renderedStep.path("Parameters").deepCopy());
            step.set("gate", renderedStep.path("Gate").deepCopy());
            step.set("forceDebugAfterOperatorConfirmation",
                    renderedStep.path("ForceDebugAfterOperatorConfirmation").deepCopy());
            ArrayNode rules = objectMapper.createArrayNode();
            for (JsonNode renderedRule : renderedStep.path("OnFailure")) {
                rules.add(toHashRule(renderedRule));
            }
            step.set("onFailure", rules);
            hashSteps.add(step);
        }
        hashSource.set("steps", hashSteps);
        return jsonCodec.sha256(jsonCodec.writeCanonical(hashSource));
    }

    private ObjectNode toHashRule(JsonNode renderedRule) {
        ObjectNode rule = objectMapper.createObjectNode();
        rule.set("vendor", renderedRule.path("Vendor").deepCopy());
        rule.set("deviceType", renderedRule.path("DeviceType").deepCopy());
        rule.set("rawCode", renderedRule.path("RawCode").deepCopy());
        rule.set("operation", renderedRule.path("Operation").deepCopy());
        rule.set("directive", renderedRule.path("Directive").deepCopy());
        rule.set("maxRetries", renderedRule.path("MaxRetries").deepCopy());
        rule.set("backoffMs", renderedRule.path("BackoffMs").deepCopy());
        JsonNode verification = renderedRule.get("Verification");
        if (verification == null || verification.isNull()) {
            rule.putNull("verification");
        } else {
            ObjectNode encoded = objectMapper.createObjectNode();
            encoded.set("operation", verification.path("Operation").deepCopy());
            encoded.set("parameters", verification.path("Parameters").deepCopy());
            rule.set("verification", encoded);
        }
        return rule;
    }

    private String requiredText(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.isTextual() || value.textValue().trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 必须是非空字符串");
        }
        return value.textValue();
    }

    private boolean requiredBoolean(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(field + " 必须是布尔值");
        }
        return value.booleanValue();
    }
}
