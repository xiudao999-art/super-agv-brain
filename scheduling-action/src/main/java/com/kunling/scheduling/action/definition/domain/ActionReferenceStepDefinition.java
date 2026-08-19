package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

public record ActionReferenceStepDefinition(
        String stepId,
        String displayName,
        String description,
        Boolean enabled,
        Integer timeoutMs,
        FailurePolicy onFailure,
        boolean gate,
        Map<String, String> outputs,
        ActionReference actionRef,
        @JsonProperty("with") Map<String, JsonNode> bindings) implements ActionStepDefinition {

    public ActionReferenceStepDefinition {
        description = description == null ? "" : description;
        onFailure = onFailure == null ? FailurePolicy.abort() : onFailure;
        outputs = outputs == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(outputs));
        bindings = bindings == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(bindings));
    }
}
