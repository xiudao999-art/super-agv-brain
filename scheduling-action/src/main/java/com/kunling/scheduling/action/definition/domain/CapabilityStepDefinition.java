package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties("capabilityVersion")
public record CapabilityStepDefinition(
        String stepId,
        String displayName,
        String description,
        Boolean enabled,
        Integer timeoutMs,
        FailurePolicy onFailure,
        boolean gate,
        Map<String, String> outputs,
        String capabilityKey,
        @JsonProperty("with") Map<String, JsonNode> bindings) implements ActionStepDefinition {

    public CapabilityStepDefinition {
        description = description == null ? "" : description;
        onFailure = onFailure == null ? FailurePolicy.abort() : onFailure;
        outputs = outputs == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(outputs));
        bindings = bindings == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(bindings));
    }
}
