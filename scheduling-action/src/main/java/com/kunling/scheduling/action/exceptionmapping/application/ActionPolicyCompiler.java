package com.kunling.scheduling.action.exceptionmapping.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionFailureDirective;
import com.kunling.scheduling.action.definition.domain.ActionFailureRule;
import com.kunling.scheduling.action.definition.domain.ActionStepDefinition;
import com.kunling.scheduling.action.exceptionmapping.domain.ActionErrorMappingRule;
import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleMatch;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 把“统一原因码策略”与固定客户端技术码、厂家原始码映射连接成下游可机械执行的 2.0 策略。
 * 业务结果不发送给下游；下游只接收可机械匹配和执行的规则。
 */
@Component
public class ActionPolicyCompiler {
    private final ObjectMapper objectMapper;
    private final ClientFaultCatalog clientFaultCatalog;

    public ActionPolicyCompiler(ObjectMapper objectMapper, ClientFaultCatalog clientFaultCatalog) {
        this.objectMapper = objectMapper;
        this.clientFaultCatalog = clientFaultCatalog;
    }

    public ActionPolicyCompilation compile(ActionDefinition definition,
                                           List<ActionErrorMappingRule> sourceRules) {
        if (definition == null) throw new IllegalArgumentException("Action 定义不能为空。");
        List<ActionErrorMappingRule> rules = ordered(sourceRules);
        Map<String, ObjectNode> policies = new LinkedHashMap<String, ObjectNode>();
        for (ActionStepDefinition step : definition.steps()) {
            policies.put(step.stepId(), compileStepPolicy(step, rules));
        }
        return new ActionPolicyCompilation(policies);
    }

    private ObjectNode compileStepPolicy(ActionStepDefinition step,
                                         List<ActionErrorMappingRule> mappings) {
        ArrayNode compiledRules = objectMapper.createArrayNode();
        for (int index = 0; index < step.onFailure().rules().size(); index++) {
            ActionFailureRule policyRule = step.onFailure().rules().get(index);
            String policyId = step.stepId() + ".rule." + (index + 1);
            int matches = 0;
            Optional<Integer> clientCode = clientFaultCatalog.findCodeByReasonCode(
                    policyRule.reasonCode());
            if (clientCode.isPresent()) {
                compiledRules.add(encodeClientWireRule(policyId, policyRule, clientCode.get()));
                matches++;
            }
            for (ActionErrorMappingRule mapping : mappings) {
                if (!appliesToOperation(mapping, step.operation())
                        || mapping.result() == null
                        || !equalsIgnoreCase(policyRule.reasonCode(), mapping.result().reasonCode())) {
                    continue;
                }
                compiledRules.add(encodeDeviceWireRule(policyId, policyRule, mapping));
                matches++;
            }
            if (matches == 0) {
                throw new IllegalArgumentException("step " + step.stepId() + " 的策略 "
                        + policyId + " 未找到 reasonCode=" + policyRule.reasonCode()
                        + " 对应的客户端技术码或已启用厂家映射。");
            }
        }
        ObjectNode policy = objectMapper.createObjectNode();
        policy.set("rules", compiledRules);
        policy.set("default", encodeDirective(step.onFailure().defaultDirective()));
        return policy;
    }

    private ObjectNode encodeClientWireRule(String policyId, ActionFailureRule policyRule, int clientCode) {
        ObjectNode when = objectMapper.createObjectNode();
        when.put("source", "CLIENT");
        when.put("code", clientCode);
        return encodeWireRule(policyId, policyRule, when);
    }

    private ObjectNode encodeDeviceWireRule(String policyId, ActionFailureRule policyRule,
                                            ActionErrorMappingRule mapping) {
        ErrorMappingRuleMatch match = mapping.match();
        ObjectNode when = objectMapper.createObjectNode();
        when.put("source", "DEVICE");
        when.put("vendor", match.vendor());
        when.put("deviceType", match.deviceType());
        when.put("code", match.rawCode());

        return encodeWireRule(policyId, policyRule, when);
    }

    private ObjectNode encodeWireRule(String policyId, ActionFailureRule policyRule, ObjectNode when) {
        ObjectNode encoded = objectMapper.createObjectNode();
        encoded.put("policyId", policyId);
        encoded.set("when", when);
        encoded.set("then", encodeDirective(policyRule.directive()));
        return encoded;
    }

    private ObjectNode encodeDirective(ActionFailureDirective directive) {
        ObjectNode encoded = objectMapper.createObjectNode();
        encoded.put("action", directive.action().name());
        if (directive.maxRetries() > 0) encoded.put("maxRetries", directive.maxRetries());
        if (directive.delayMs() > 0) encoded.put("delayMs", directive.delayMs());
        if (directive.verifyOperation() != null) {
            ObjectNode verify = objectMapper.createObjectNode();
            verify.put("operation", directive.verifyOperation());
            verify.set("params", directive.verifyParams() == null
                    ? objectMapper.createObjectNode() : directive.verifyParams().deepCopy());
            encoded.set("verify", verify);
        }
        if (directive.onExhaust() != null) encoded.put("onExhaust", directive.onExhaust().name());
        return encoded;
    }

    private boolean appliesToOperation(ActionErrorMappingRule rule, String operation) {
        return rule != null && rule.match() != null
                && (rule.match().operation() == null
                || equalsIgnoreCase(rule.match().operation(), operation));
    }

    private List<ActionErrorMappingRule> ordered(List<ActionErrorMappingRule> source) {
        List<ActionErrorMappingRule> result = source == null
                ? new ArrayList<ActionErrorMappingRule>() : new ArrayList<ActionErrorMappingRule>(source);
        result.removeIf(rule -> rule == null || rule.match() == null);
        result.sort(Comparator.comparingInt(ActionErrorMappingRule::priority).reversed()
                .thenComparing(rule -> rule.ruleId() == null ? "" : rule.ruleId()));
        return result;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
