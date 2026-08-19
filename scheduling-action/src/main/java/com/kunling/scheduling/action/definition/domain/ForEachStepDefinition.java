package com.kunling.scheduling.action.definition.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ForEachStepDefinition(
        String stepId,
        String displayName,
        String description,
        Boolean enabled,
        Integer timeoutMs,
        FailurePolicy onFailure,
        boolean gate,
        Map<String, String> outputs,
        String items,
        String itemVariable,
        int maxIterations,
        OrderByDefinition orderBy,
        List<ActionStepDefinition> steps) implements ActionStepDefinition {

    public ForEachStepDefinition {
        description = description == null ? "" : description;
        onFailure = onFailure == null ? FailurePolicy.abort() : onFailure;
        outputs = outputs == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(outputs));
        itemVariable = itemVariable == null || itemVariable.isBlank() ? "$item" : itemVariable;
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
