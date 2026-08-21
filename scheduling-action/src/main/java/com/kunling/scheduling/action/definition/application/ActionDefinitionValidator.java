package com.kunling.scheduling.action.definition.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionPhaseDefinition;
import com.kunling.scheduling.action.definition.domain.PhaseFailureAction;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 在保存和启用 Action 时集中校验下游协议结构与引用关系。 */
@Component
public class ActionDefinitionValidator {

    private static final Pattern ACTION_KEY = Pattern.compile("[A-Z0-9][A-Z0-9._-]{1,127}");
    private static final Pattern PHASE_ID = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,127}");
    private static final Pattern BINDING = Pattern.compile("^\\$parameters\\.([A-Za-z0-9_.-]+)$");
    private static final Set<String> POLICY_PARAMETER_NAMES = new HashSet<String>();

    static {
        POLICY_PARAMETER_NAMES.add("maxRetries");
        POLICY_PARAMETER_NAMES.add("retryFromPhaseId");
        POLICY_PARAMETER_NAMES.add("onExhaust");
    }

    /** 草稿允许暂时没有 Phase，但身份字段必须完整，避免产生无法再次定位的脏数据。 */
    public void validateDraft(ActionDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Action definition 不能为空。");
        }
        requireText(definition.actionKey(), "actionKey");
        if (!ACTION_KEY.matcher(definition.actionKey()).matches()) {
            throw new IllegalArgumentException("actionKey 只能包含大写字母、数字、点、下划线和短横线，长度为 2-128。");
        }
        requireText(definition.displayName(), "displayName");
        if (definition.downstreamActionType() == null) {
            throw new IllegalArgumentException("downstreamActionType 不能为空。");
        }
        if (definition.timeoutMs() < 1_000 || definition.timeoutMs() > 3_600_000) {
            throw new IllegalArgumentException("timeoutMs 必须在 1000-3600000 之间。");
        }
    }

    /** 启用和执行前执行完整校验。 */
    public void validateExecutable(ActionDefinition definition) {
        validateDraft(definition);
        if (definition.phases().isEmpty()) {
            throw new IllegalArgumentException("Action 至少需要一个 phase。");
        }
        if (definition.phases().size() > 200) {
            throw new IllegalArgumentException("单个 Action 最多允许 200 个 phase。");
        }

        Map<String, Integer> indexes = new HashMap<String, Integer>();
        boolean hasEnabledPhase = false;
        for (int index = 0; index < definition.phases().size(); index++) {
            ActionPhaseDefinition phase = definition.phases().get(index);
            validatePhase(definition, phase, index, indexes);
            indexes.put(phase.phaseId(), index);
            hasEnabledPhase |= phase.enabled();
        }
        if (!hasEnabledPhase) {
            throw new IllegalArgumentException("Action 至少需要一个启用的 phase。");
        }
    }

    private void validatePhase(ActionDefinition definition,
                               ActionPhaseDefinition phase,
                               int index,
                               Map<String, Integer> indexes) {
        if (phase == null) {
            throw new IllegalArgumentException("phases[" + index + "] 不能为空。");
        }
        requireText(phase.phaseId(), "phases[" + index + "].phaseId");
        if (!PHASE_ID.matcher(phase.phaseId()).matches()) {
            throw new IllegalArgumentException("phaseId 格式无效：" + phase.phaseId());
        }
        if (indexes.containsKey(phase.phaseId())) {
            throw new IllegalArgumentException("phaseId 重复：" + phase.phaseId());
        }
        if (phase.subAction() == null) {
            throw new IllegalArgumentException("phase " + phase.phaseId() + " 缺少 subAction。");
        }
        if (!definition.downstreamActionType().supports(phase.subAction())) {
            throw new IllegalArgumentException(definition.downstreamActionType().wireName()
                    + " 不允许使用子动作 " + phase.subAction().wireName() + "。");
        }
        if (phase.parameters() == null || !phase.parameters().isObject()) {
            throw new IllegalArgumentException("phase " + phase.phaseId() + " 的 params 必须是 JSON 对象。");
        }
        for (String reserved : POLICY_PARAMETER_NAMES) {
            if (phase.parameters().has(reserved)) {
                throw new IllegalArgumentException("phase " + phase.phaseId() + " 的 " + reserved
                        + " 必须使用异常策略字段配置，不能重复写入 params。");
            }
        }
        for (String requiredParameter : phase.subAction().requiredParameters()) {
            JsonNode value = phase.parameters().get(requiredParameter);
            if (value == null || value.isNull() || (value.isTextual() && value.textValue().trim().isEmpty())) {
                throw new IllegalArgumentException("phase " + phase.phaseId() + " 的子动作 "
                        + phase.subAction().wireName() + " 缺少必填参数 " + requiredParameter + "。");
            }
        }
        if (phase.maxRetries() < 0 || phase.maxRetries() > 10) {
            throw new IllegalArgumentException("phase " + phase.phaseId() + " 的 maxRetries 必须在 0-10 之间。");
        }
        if ((phase.onFail() == PhaseFailureAction.RETRY_PHASE
                || phase.onFail() == PhaseFailureAction.VERIFY_BEFORE_RETRY) && phase.maxRetries() == 0) {
            throw new IllegalArgumentException("phase " + phase.phaseId() + " 配置重试策略时 maxRetries 必须大于 0。");
        }
        if (phase.gate() && phase.onFail() == PhaseFailureAction.SKIP) {
            throw new IllegalArgumentException("闸门 phase " + phase.phaseId() + " 不允许配置 SKIP。");
        }
        if (phase.retryFromPhaseId() != null) {
            Integer target = indexes.get(phase.retryFromPhaseId());
            if (target == null || target >= index) {
                throw new IllegalArgumentException("phase " + phase.phaseId()
                        + " 的 retryFromPhaseId 必须指向此前 phase。");
            }
            if (phase.onFail() != PhaseFailureAction.VERIFY_BEFORE_RETRY) {
                throw new IllegalArgumentException("retryFromPhaseId 只允许与 VERIFY_BEFORE_RETRY 一起使用。");
            }
        }
        validateBindings(phase.parameters(), definition, "phase " + phase.phaseId() + ".params");
    }

    private void validateBindings(JsonNode node, ActionDefinition definition, String path) {
        if (node.isTextual()) {
            String value = node.textValue();
            if (value.startsWith("$input.")) {
                throw new IllegalArgumentException(path
                        + " 使用了已移除的 $input 绑定，请改用 $parameters 并在设备联调参数 Schema 中声明。");
            }
            if (!value.startsWith("$parameters.")) {
                return;
            }
            Matcher matcher = BINDING.matcher(value);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(path + " 包含无效绑定：" + value);
            }
            String root = matcher.group(1).split("\\.", 2)[0];
            if (!definition.parameterSchema().containsKey(root)) {
                throw new IllegalArgumentException(path + " 引用了未声明参数：" + value);
            }
            return;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                validateBindings(node.get(index), definition, path + "[" + index + "]");
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                validateBindings(field.getValue(), definition, path + "." + field.getKey());
            }
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空。");
        }
    }
}
