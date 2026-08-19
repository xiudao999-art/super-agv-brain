package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.compilation.domain.ConditionGuard;
import com.kunling.scheduling.action.compilation.domain.ExecutionNode;
import com.kunling.scheduling.action.compilation.domain.ExecutionPlan;
import com.kunling.scheduling.action.compilation.domain.LoopFrame;
import com.kunling.scheduling.action.definition.domain.ConditionExpression;
import com.kunling.scheduling.action.definition.domain.OrderByDefinition;
import com.kunling.scheduling.action.definition.domain.SortDirection;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将编译期的有界循环槽位和条件分支转换为本次实例实际需要执行的原子节点。
 * 物化过程不执行用户脚本，仅支持白名单表达式和操作符，因此结果可复现、可审计。
 */
@Component
public class ExecutionPlanMaterializer {

    private final ExecutionValueResolver valueResolver;

    public ExecutionPlanMaterializer(ExecutionValueResolver valueResolver) {
        this.valueResolver = valueResolver;
    }

    public List<ExecutionNode> materialize(ExecutionPlan plan, JsonNode input, JsonNode context) {
        List<ExecutionNode> materialized = new ArrayList<>();
        for (ExecutionNode node : plan.nodes()) {
            Map<String, JsonNode> loopValues = resolveLoopValues(node.loops(), input, context);
            if (loopValues == null || !matchesGuards(node.guards(), loopValues, input, context)) {
                continue;
            }
            Map<String, JsonNode> bindings = new LinkedHashMap<>();
            node.bindings().forEach((name, value) -> bindings.put(name, replaceLoopTokens(value, loopValues)));
            materialized.add(node.materialized(bindings));
        }
        return List.copyOf(materialized);
    }

    /** 返回 null 表示当前编译槽位超出了实际数组长度，该节点不参与本次执行。 */
    private Map<String, JsonNode> resolveLoopValues(
            List<LoopFrame> loops,
            JsonNode input,
            JsonNode context) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        for (LoopFrame loop : loops) {
            JsonNode expression = replaceLoopTokens(
                    JsonNodeFactory.instance.textNode(loop.itemsExpression()), values);
            JsonNode items = valueResolver.resolveValue(expression, input, context, Map.of());
            if (!items.isArray()) {
                throw new IllegalArgumentException("循环节点 " + loop.stepId() + " 的 items 解析结果不是数组。");
            }
            List<JsonNode> orderedItems = orderedItems((ArrayNode) items, loop.orderBy());
            if (orderedItems.size() > loop.maxIterations()) {
                throw new IllegalArgumentException("循环节点 " + loop.stepId() + " 实际元素数量 "
                        + orderedItems.size() + " 超过上限 " + loop.maxIterations() + "。");
            }
            if (loop.iterationIndex() >= orderedItems.size()) {
                return null;
            }
            values.put(loop.itemToken(), orderedItems.get(loop.iterationIndex()).deepCopy());
        }
        return values;
    }

    private List<JsonNode> orderedItems(ArrayNode items, OrderByDefinition orderBy) {
        List<JsonNode> ordered = new ArrayList<>();
        items.forEach(item -> ordered.add(item.deepCopy()));
        if (orderBy == null || orderBy.property() == null || orderBy.property().isBlank()) {
            return ordered;
        }
        Comparator<JsonNode> comparator = (left, right) -> compare(
                navigate(left, orderBy.property()), navigate(right, orderBy.property()));
        if (orderBy.direction() == SortDirection.DESCENDING) {
            comparator = comparator.reversed();
        }
        // List.sort 是稳定排序，相同排序键仍保持调度输入中的先后次序。
        ordered.sort(comparator);
        return ordered;
    }

    private boolean matchesGuards(
            List<ConditionGuard> guards,
            Map<String, JsonNode> loopValues,
            JsonNode input,
            JsonNode context) {
        for (ConditionGuard guard : guards) {
            ConditionExpression condition = guard.condition();
            JsonNode left = valueResolver.resolveValue(
                    replaceLoopTokens(condition.left(), loopValues), input, context, Map.of());
            JsonNode right = condition.right() == null
                    ? JsonNodeFactory.instance.nullNode()
                    : valueResolver.resolveValue(
                            replaceLoopTokens(condition.right(), loopValues), input, context, Map.of());
            if (evaluate(condition, left, right) != guard.expected()) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluate(ConditionExpression condition, JsonNode left, JsonNode right) {
        return switch (condition.operator()) {
            case EQUAL -> left.equals(right);
            case NOT_EQUAL -> !left.equals(right);
            case GREATER_THAN -> compare(left, right) > 0;
            case GREATER_THAN_OR_EQUAL -> compare(left, right) >= 0;
            case LESS_THAN -> compare(left, right) < 0;
            case LESS_THAN_OR_EQUAL -> compare(left, right) <= 0;
            case IS_TRUE -> left.isBoolean() && left.booleanValue();
            case IS_FALSE -> left.isBoolean() && !left.booleanValue();
            case IS_NULL -> left.isNull();
            case IS_NOT_NULL -> !left.isNull();
            case CONTAINS -> contains(left, right);
        };
    }

    private boolean contains(JsonNode container, JsonNode expected) {
        if (container.isArray()) {
            for (JsonNode item : container) {
                if (item.equals(expected)) {
                    return true;
                }
            }
            return false;
        }
        if (container.isTextual() && expected.isTextual()) {
            return container.textValue().contains(expected.textValue());
        }
        if (container.isObject() && expected.isTextual()) {
            return container.has(expected.textValue());
        }
        throw new IllegalArgumentException("CONTAINS 仅支持数组、字符串或对象键判断。");
    }

    private int compare(JsonNode left, JsonNode right) {
        if (left == null || left.isMissingNode() || left.isNull()) {
            return right == null || right.isMissingNode() || right.isNull() ? 0 : -1;
        }
        if (right == null || right.isMissingNode() || right.isNull()) {
            return 1;
        }
        if (left.isNumber() && right.isNumber()) {
            return new BigDecimal(left.asText()).compareTo(new BigDecimal(right.asText()));
        }
        if (left.isTextual() && right.isTextual()) {
            return left.textValue().compareTo(right.textValue());
        }
        if (left.isBoolean() && right.isBoolean()) {
            return Boolean.compare(left.booleanValue(), right.booleanValue());
        }
        throw new IllegalArgumentException("条件比较两侧类型不兼容：" + left.getNodeType() + " 与 " + right.getNodeType());
    }

    private JsonNode navigate(JsonNode root, String path) {
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            current = current == null ? null : current.get(segment);
            if (current == null || current.isMissingNode()) {
                throw new IllegalArgumentException("排序字段不存在：" + path);
            }
        }
        return current;
    }

    private JsonNode replaceLoopTokens(JsonNode value, Map<String, JsonNode> loopValues) {
        if (value == null || value.isNull() || loopValues.isEmpty()) {
            return value == null ? JsonNodeFactory.instance.nullNode() : value.deepCopy();
        }
        if (value.isTextual()) {
            String expression = value.textValue();
            // 较长 token 优先，可避免嵌套循环的前缀出现歧义。
            List<String> tokens = loopValues.keySet().stream()
                    .sorted(Comparator.comparingInt(String::length).reversed())
                    .toList();
            for (String token : tokens) {
                if (expression.equals(token)) {
                    return loopValues.get(token).deepCopy();
                }
                if (expression.startsWith(token + ".")) {
                    return navigate(loopValues.get(token), expression.substring(token.length() + 1)).deepCopy();
                }
            }
            return value.deepCopy();
        }
        if (value.isObject()) {
            ObjectNode object = JsonNodeFactory.instance.objectNode();
            value.properties().forEach(entry ->
                    object.set(entry.getKey(), replaceLoopTokens(entry.getValue(), loopValues)));
            return object;
        }
        if (value.isArray()) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> array.add(replaceLoopTokens(item, loopValues)));
            return array;
        }
        return value.deepCopy();
    }
}
