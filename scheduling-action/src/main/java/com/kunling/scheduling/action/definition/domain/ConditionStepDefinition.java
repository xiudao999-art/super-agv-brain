package com.kunling.scheduling.action.definition.domain;

import com.kunling.scheduling.action.shared.ImmutableCollections;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ConditionStepDefinition implements ActionStepDefinition {
    String stepId;
    String displayName;
    String description;
    Boolean enabled;
    Integer timeoutMs;
    FailurePolicy onFailure;
    boolean gate;
    Map<String, String> outputs;
    ConditionExpression condition;
    List<ActionStepDefinition> then;
    @com.fasterxml.jackson.annotation.JsonProperty("else") List<ActionStepDefinition> elseSteps;
    @ConstructorProperties({"stepId", "displayName", "description", "enabled", "timeoutMs", "onFailure", "gate", "outputs", "condition", "then", "elseSteps"})
    public ConditionStepDefinition(
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
            @com.fasterxml.jackson.annotation.JsonProperty("else") List<ActionStepDefinition> elseSteps
    ) {
        description = description == null ? "" : description;
        onFailure = onFailure == null ? FailurePolicy.abort() : onFailure;
        outputs = outputs == null ? ImmutableCollections.mapOf() : ImmutableCollections.copyMap(new LinkedHashMap<>(outputs));
        then = then == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(then);
        elseSteps = elseSteps == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(elseSteps);
        this.stepId = stepId;
        this.displayName = displayName;
        this.description = description;
        this.enabled = enabled;
        this.timeoutMs = timeoutMs;
        this.onFailure = onFailure;
        this.gate = gate;
        this.outputs = outputs;
        this.condition = condition;
        this.then = then;
        this.elseSteps = elseSteps;
    }

}
