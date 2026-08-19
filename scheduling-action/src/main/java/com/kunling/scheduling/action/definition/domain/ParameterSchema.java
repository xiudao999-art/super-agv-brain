package com.kunling.scheduling.action.definition.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

public record ParameterSchema(
        ParameterType type,
        boolean required,
        String unit,
        List<String> enumValues,
        Map<String, ParameterSchema> properties,
        ParameterSchema items,
        BigDecimal minimum,
        BigDecimal maximum) {

    public ParameterSchema {
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        properties = properties == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(properties));
    }

    public ParameterSchema(ParameterType type, boolean required, String unit,
                           List<String> enumValues, Map<String, ParameterSchema> properties,
                           ParameterSchema items) {
        this(type, required, unit, enumValues, properties, items, null, null);
    }
}
