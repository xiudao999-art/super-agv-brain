package com.kunling.scheduling.action.definition.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ConditionStepDefinition(
        String stepId,
        String displayName,
        String description,
        Boolean enabled,
        Integer timeoutMs,
        FailurePolicy onFailure,
        boolean gate,
        Map<String, String> outputs,
        ConditionExpression condition,
        List<ActionStepDefinition> then,
        @com.fasterxml.jackson.annotation.JsonProperty("else") List<ActionStepDefinition> elseSteps) implements ActionStepDefinition {

    public ConditionStepDefinition {
        description = description == null ? "" : description;
        onFailure = onFailure == null ? FailurePolicy.abort() : onFailure;
        outputs = outputs == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(outputs));
        then = then == null ? List.of() : List.copyOf(then);
        elseSteps = elseSteps == null ? List.of() : List.copyOf(elseSteps);
    }

}
