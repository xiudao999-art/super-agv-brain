package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.commissioning.application.ActionParameterSetView;
import com.kunling.scheduling.action.definition.application.ActionDefinitionValidator;
import com.kunling.scheduling.action.definition.application.ActionDefinitionView;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionPhaseDefinition;
import com.kunling.scheduling.action.config.JsonCodec;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

/**
 * 把当前 Action、联调参数和本次业务输入一次性物化为 cnet8 可执行的完整动作包。
 *
 * <p>该模块是协议转换的唯一 seam；执行模块只保存和发送它的不可变结果。</p>
 */
@Component
public class ActionPackageAssembler {

    public static final String PROTOCOL_ACTION_VERSION = "1.0";

    private final ObjectMapper objectMapper;
    private final JsonCodec jsonCodec;
    private final ActionDefinitionValidator definitionValidator;
    private final ActionInputValidator inputValidator;

    public ActionPackageAssembler(ObjectMapper objectMapper,
                                  JsonCodec jsonCodec,
                                  ActionDefinitionValidator definitionValidator,
                                  ActionInputValidator inputValidator) {
        this.objectMapper = objectMapper;
        this.jsonCodec = jsonCodec;
        this.definitionValidator = definitionValidator;
        this.inputValidator = inputValidator;
    }

    public ActionPackagePreview assemble(ActionDefinitionView action,
                                         ActionParameterSetView parameterSet,
                                         JsonNode input,
                                         String robotId) {
        if (action == null) {
            throw new IllegalArgumentException("Action 不能为空。");
        }
        ActionDefinition definition = action.definition();
        definitionValidator.validateExecutable(definition);

        ObjectNode safeInput = requireObject(input, "input");
        inputValidator.validate(safeInput, definition.inputSchema());
        ObjectNode parameters = resolveParameterValues(definition, parameterSet, robotId);

        ArrayNode phases = objectMapper.createArrayNode();
        for (ActionPhaseDefinition phase : definition.phases()) {
            phases.add(encodePhase(phase, safeInput, parameters));
        }

        ObjectNode mainAction = objectMapper.createObjectNode();
        mainAction.put("templateId", definition.actionKey());
        mainAction.put("actionType", definition.downstreamActionType().wireName());
        mainAction.set("phases", phases);

        ObjectNode commandInput = objectMapper.createObjectNode();
        // MainAction 的首字母大写是双方既有线协议的一部分。
        commandInput.set("MainAction", mainAction);
        String packageHash = jsonCodec.sha256(jsonCodec.writeCanonical(commandInput));

        JsonNode parameterSnapshot = parameterSet == null
                ? JsonNodeFactory.instance.objectNode()
                : objectMapper.valueToTree(parameterSet);
        return new ActionPackagePreview(definition.actionKey(), action.revision(),
                definition.downstreamActionType().wireName(),
                parameterSet == null ? null : parameterSet.id(),
                parameterSet == null ? null : parameterSet.revision(),
                PROTOCOL_ACTION_VERSION, packageHash, definition.timeoutMs(),
                objectMapper.valueToTree(definition), parameterSnapshot,
                safeInput.deepCopy(), commandInput, phases.deepCopy());
    }

    private ObjectNode resolveParameterValues(ActionDefinition definition,
                                              ActionParameterSetView parameterSet,
                                              String robotId) {
        if (parameterSet == null) {
            if (!definition.parameterSchema().isEmpty()) {
                throw new IllegalArgumentException("Action 声明了设备联调参数，执行前必须选择参数集。");
            }
            return objectMapper.createObjectNode();
        }
        if (!parameterSet.actionKey().equals(definition.actionKey())) {
            throw new IllegalArgumentException("参数集与 Action 不匹配。");
        }
        if (!parameterSet.enabled()) {
            throw new IllegalArgumentException("参数集已停用：" + parameterSet.id());
        }
        if (parameterSet.robotId() != null && !parameterSet.robotId().equals(robotId)) {
            throw new IllegalArgumentException("参数集绑定机器人 " + parameterSet.robotId()
                    + "，不能用于 " + robotId + "。");
        }
        ObjectNode values = requireObject(parameterSet.values(), "parameterSet.values");
        inputValidator.validate(values, definition.parameterSchema());
        return values;
    }

    private ObjectNode encodePhase(ActionPhaseDefinition phase, ObjectNode input, ObjectNode parameters) {
        ObjectNode encoded = objectMapper.createObjectNode();
        encoded.put("phaseId", phase.phaseId());
        encoded.put("subAction", phase.subAction().wireName());
        encoded.put("enabled", phase.enabled());
        ObjectNode resolvedParameters = requireObject(
                resolveNode(phase.parameters(), input, parameters, "$phase." + phase.phaseId() + ".params"),
                "phase " + phase.phaseId() + ".params");
        resolvedParameters.put("maxRetries", phase.maxRetries());
        if (phase.retryFromPhaseId() != null) {
            resolvedParameters.put("retryFromPhaseId", phase.retryFromPhaseId());
        }
        resolvedParameters.put("onExhaust", phase.onExhaust().name());
        encoded.set("params", resolvedParameters);
        encoded.put("gate", phase.gate());
        encoded.put("onFail", phase.onFail().name());
        return encoded;
    }

    private JsonNode resolveNode(JsonNode source,
                                 ObjectNode input,
                                 ObjectNode parameters,
                                 String path) {
        if (source == null || source.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (source.isTextual()) {
            String value = source.textValue();
            if (value.startsWith("$input.")) {
                return requiredBinding(input, value.substring("$input.".length()), value, path);
            }
            if (value.startsWith("$parameters.")) {
                return requiredBinding(parameters, value.substring("$parameters.".length()), value, path);
            }
            return source.deepCopy();
        }
        if (source.isArray()) {
            ArrayNode target = objectMapper.createArrayNode();
            for (int index = 0; index < source.size(); index++) {
                target.add(resolveNode(source.get(index), input, parameters, path + "[" + index + "]"));
            }
            return target;
        }
        if (source.isObject()) {
            ObjectNode target = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                target.set(field.getKey(), resolveNode(field.getValue(), input, parameters,
                        path + "." + field.getKey()));
            }
            return target;
        }
        return source.deepCopy();
    }

    private JsonNode requiredBinding(JsonNode root, String dottedPath, String binding, String path) {
        JsonNode current = root;
        for (String segment : dottedPath.split("\\.")) {
            if (current == null || current.isNull()) {
                throw new IllegalArgumentException(path + " 无法解析绑定 " + binding + "。");
            }
            if (current.isArray() && segment.matches("\\d+")) {
                int index = Integer.parseInt(segment);
                current = index < current.size() ? current.get(index) : null;
            } else {
                current = current.get(segment);
            }
        }
        if (current == null || current.isNull()) {
            throw new IllegalArgumentException(path + " 无法解析绑定 " + binding + "。");
        }
        return current.deepCopy();
    }

    private ObjectNode requireObject(JsonNode value, String field) {
        if (value == null || value.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!value.isObject()) {
            throw new IllegalArgumentException(field + " 必须是 JSON 对象。");
        }
        return (ObjectNode) value.deepCopy();
    }
}
