package com.kunling.scheduling.action.definition.domain;

import com.kunling.scheduling.action.shared.ImmutableCollections;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

@Schema(description = "Action 输入或联调参数的约束")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ParameterSchema {
    @Schema(description = "参数类型")
    ParameterType type;
    @Schema(description = "是否必填")
    boolean required;
    @Schema(description = "工程单位，例如 mm、degree、ms")
    String unit;
    @Schema(description = "允许的枚举值")
    List<String> enumValues;
    @Schema(description = "对象属性约束")
    Map<String, ParameterSchema> properties;
    @Schema(description = "数组元素约束")
    ParameterSchema items;
    @Schema(description = "允许的最小值")
    BigDecimal minimum;
    @Schema(description = "允许的最大值")
    BigDecimal maximum;
    @ConstructorProperties({"type", "required", "unit", "enumValues", "properties", "items", "minimum", "maximum"})
    public ParameterSchema(
            ParameterType type,
            boolean required,
            String unit,
            List<String> enumValues,
            Map<String, ParameterSchema> properties,
            ParameterSchema items,
            BigDecimal minimum,
            BigDecimal maximum
    ) {
        enumValues = enumValues == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(enumValues);
        properties = properties == null ? ImmutableCollections.mapOf() : ImmutableCollections.copyMap(new LinkedHashMap<>(properties));
        this.type = type;
        this.required = required;
        this.unit = unit;
        this.enumValues = enumValues;
        this.properties = properties;
        this.items = items;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public ParameterSchema(ParameterType type, boolean required, String unit,
                           List<String> enumValues, Map<String, ParameterSchema> properties,
                           ParameterSchema items) {
        this(type, required, unit, enumValues, properties, items, null, null);
    }
}
