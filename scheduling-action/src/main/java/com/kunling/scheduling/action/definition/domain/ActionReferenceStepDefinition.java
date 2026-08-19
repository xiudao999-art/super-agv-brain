package com.kunling.scheduling.action.definition.domain;

import com.kunling.scheduling.action.shared.ImmutableCollections;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionReferenceStepDefinition implements ActionStepDefinition {
    String stepId;
    String displayName;
    String description;
    Boolean enabled;
    Integer timeoutMs;
    FailurePolicy onFailure;
    boolean gate;
    Map<String, String> outputs;
    ActionReference actionRef;
    @JsonProperty("with") Map<String, JsonNode> bindings;
    @ConstructorProperties({"stepId", "displayName", "description", "enabled", "timeoutMs", "onFailure", "gate", "outputs", "actionRef", "bindings"})
    public ActionReferenceStepDefinition(
            String stepId,
            String displayName,
            String description,
            Boolean enabled,
            Integer timeoutMs,
            FailurePolicy onFailure,
            boolean gate,
            Map<String, String> outputs,
            ActionReference actionRef,
            @JsonProperty("with") Map<String, JsonNode> bindings
    ) {
        description = description == null ? "" : description;
        onFailure = onFailure == null ? FailurePolicy.abort() : onFailure;
        outputs = outputs == null ? ImmutableCollections.mapOf() : ImmutableCollections.copyMap(new LinkedHashMap<>(outputs));
        bindings = bindings == null ? ImmutableCollections.mapOf() : ImmutableCollections.copyMap(new LinkedHashMap<>(bindings));
        this.stepId = stepId;
        this.displayName = displayName;
        this.description = description;
        this.enabled = enabled;
        this.timeoutMs = timeoutMs;
        this.onFailure = onFailure;
        this.gate = gate;
        this.outputs = outputs;
        this.actionRef = actionRef;
        this.bindings = bindings;
    }
}
