package com.kunling.scheduling.action.definition.domain;

import com.kunling.scheduling.action.shared.ImmutableCollections;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ParameterSchema {
    ParameterType type;
    boolean required;
    String unit;
    List<String> enumValues;
    Map<String, ParameterSchema> properties;
    ParameterSchema items;
    BigDecimal minimum;
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
