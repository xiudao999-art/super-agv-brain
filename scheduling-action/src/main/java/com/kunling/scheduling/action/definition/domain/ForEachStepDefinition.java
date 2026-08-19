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
public class ForEachStepDefinition implements ActionStepDefinition {
    String stepId;
    String displayName;
    String description;
    Boolean enabled;
    Integer timeoutMs;
    FailurePolicy onFailure;
    boolean gate;
    Map<String, String> outputs;
    String items;
    String itemVariable;
    int maxIterations;
    OrderByDefinition orderBy;
    List<ActionStepDefinition> steps;
    @ConstructorProperties({"stepId", "displayName", "description", "enabled", "timeoutMs", "onFailure", "gate", "outputs", "items", "itemVariable", "maxIterations", "orderBy", "steps"})
    public ForEachStepDefinition(
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
            List<ActionStepDefinition> steps
    ) {
        description = description == null ? "" : description;
        onFailure = onFailure == null ? FailurePolicy.abort() : onFailure;
        outputs = outputs == null ? ImmutableCollections.mapOf() : ImmutableCollections.copyMap(new LinkedHashMap<>(outputs));
        itemVariable = itemVariable == null || itemVariable.trim().isEmpty() ? "$item" : itemVariable;
        steps = steps == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(steps);
        this.stepId = stepId;
        this.displayName = displayName;
        this.description = description;
        this.enabled = enabled;
        this.timeoutMs = timeoutMs;
        this.onFailure = onFailure;
        this.gate = gate;
        this.outputs = outputs;
        this.items = items;
        this.itemVariable = itemVariable;
        this.maxIterations = maxIterations;
        this.orderBy = orderBy;
        this.steps = steps;
    }
}
