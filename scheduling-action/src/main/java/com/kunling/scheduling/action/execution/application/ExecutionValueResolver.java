package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class ExecutionValueResolver {

    public ObjectNode resolveBindings(
            Map<String, JsonNode> bindings,
            JsonNode input,
            JsonNode context,
            Map<String, JsonNode> stepOutputs) {
        ObjectNode resolved = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        bindings.forEach((name, value) -> resolved.set(name, resolveValue(value, input, context, stepOutputs)));
        return resolved;
    }

    /**
     * 解析一个运行时值。该入口同时供参数绑定和执行计划物化使用，确保两处遵循完全相同的表达式语义。
     */
    public JsonNode resolveValue(
            JsonNode value,
            JsonNode input,
            JsonNode context,
            Map<String, JsonNode> stepOutputs) {
        if (value == null || value.isNull()) {
            return com.fasterxml.jackson.databind.node.NullNode.instance;
        }
        if (value.isTextual() && value.textValue().startsWith("$")) {
            return resolveExpression(value.textValue(), input, context, stepOutputs);
        }
        if (value.isObject()) {
            ObjectNode object = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            value.properties().forEach(entry -> object.set(entry.getKey(),
                    resolveValue(entry.getValue(), input, context, stepOutputs)));
            return object;
        }
        if (value.isArray()) {
            ArrayNode array = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> array.add(resolveValue(item, input, context, stepOutputs)));
            return array;
        }
        return value.deepCopy();
    }

    private JsonNode resolveExpression(
            String expression,
            JsonNode input,
            JsonNode context,
            Map<String, JsonNode> stepOutputs) {
        if (expression.equals("$input")) {
            return required(input, expression).deepCopy();
        }
        if (expression.startsWith("$input.")) {
            return navigate(required(input, "$input"), expression.substring("$input.".length()), expression).deepCopy();
        }
        if (expression.equals("$context")) {
            return required(context, expression).deepCopy();
        }
        if (expression.startsWith("$context.")) {
            return navigate(required(context, "$context"), expression.substring("$context.".length()), expression).deepCopy();
        }
        if (expression.startsWith("$steps.")) {
            return resolveStepExpression(expression, stepOutputs).deepCopy();
        }
        throw new IllegalArgumentException("不支持的运行时表达式：" + expression);
    }

    private JsonNode resolveStepExpression(String expression, Map<String, JsonNode> outputs) {
        List<String> nodeIds = new ArrayList<>(outputs.keySet());
        nodeIds.sort(Comparator.comparingInt(String::length).reversed());
        for (String nodeId : nodeIds) {
            String prefix = "$steps." + nodeId + ".output";
            if (expression.equals(prefix)) {
                return required(outputs.get(nodeId), expression);
            }
            if (expression.startsWith(prefix + ".")) {
                return navigate(required(outputs.get(nodeId), prefix), expression.substring(prefix.length() + 1), expression);
            }
        }
        throw new IllegalArgumentException("表达式引用了尚未完成或不存在的节点：" + expression);
    }

    private JsonNode navigate(JsonNode root, String path, String expression) {
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            current = current == null ? null : current.get(segment);
            if (current == null || current.isMissingNode()) {
                throw new IllegalArgumentException("运行时表达式无法解析：" + expression);
            }
        }
        return current;
    }

    private JsonNode required(JsonNode value, String expression) {
        if (value == null || value.isMissingNode()) {
            throw new IllegalArgumentException("运行时表达式无法解析：" + expression);
        }
        return value;
    }
}
