package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.definition.domain.ParameterSchema;
import com.kunling.scheduling.action.definition.domain.ParameterType;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 在任何机器人命令发出前校验主 Action 输入，避免把配置错误带入物理执行。 */
@Component
public class ActionInputValidator {

    public void validate(JsonNode input, Map<String, ParameterSchema> schema) {
        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException("Action input 必须是 JSON 对象。");
        }
        validateObject(input, schema, "$input");
    }

    private void validateObject(JsonNode value, Map<String, ParameterSchema> schema, String path) {
        schema.forEach((name, parameter) -> {
            JsonNode child = value.get(name);
            if (parameter.required() && (child == null || child.isNull())) {
                throw new IllegalArgumentException(path + "." + name + " 是必填参数。");
            }
            if (child != null && !child.isNull()) {
                validateValue(child, parameter, path + "." + name);
            }
        });
        value.fieldNames().forEachRemaining(name -> {
            if (!schema.containsKey(name)) {
                throw new IllegalArgumentException(path + " 包含未声明参数 " + name + "。");
            }
        });
    }

    private void validateValue(JsonNode value, ParameterSchema schema, String path) {
        if (!matchesType(value, schema.type())) {
            throw new IllegalArgumentException(path + " 类型应为 " + schema.type() + "。");
        }
        if (!schema.enumValues().isEmpty()
                && (!value.isTextual() || !schema.enumValues().contains(value.textValue()))) {
            throw new IllegalArgumentException(path + " 不在允许值 " + schema.enumValues() + " 中。");
        }
        if (value.isNumber()) {
            java.math.BigDecimal number = value.decimalValue();
            if (schema.minimum() != null && number.compareTo(schema.minimum()) < 0) {
                throw new IllegalArgumentException(path + " 不能小于 " + schema.minimum() + "。");
            }
            if (schema.maximum() != null && number.compareTo(schema.maximum()) > 0) {
                throw new IllegalArgumentException(path + " 不能大于 " + schema.maximum() + "。");
            }
        }
        if (schema.type() == ParameterType.OBJECT) {
            validateObject(value, schema.properties(), path);
        }
        if (schema.type() == ParameterType.ARRAY && schema.items() != null) {
            for (int index = 0; index < value.size(); index++) {
                validateValue(value.get(index), schema.items(), path + "[" + index + "]");
            }
        }
    }

    private boolean matchesType(JsonNode value, ParameterType type) {
        if (type == null) {
            throw new IllegalArgumentException("参数 Schema 缺少 type。");
        }
        switch (type) {
            case STRING:
                return value.isTextual();
            case NUMBER:
                return value.isNumber();
            case INTEGER:
                return value.isIntegralNumber();
            case BOOLEAN:
                return value.isBoolean();
            case OBJECT:
                return value.isObject();
            case ARRAY:
                return value.isArray();
            default:
                throw new IllegalArgumentException("不支持的参数类型：" + type);
        }
    }
}
