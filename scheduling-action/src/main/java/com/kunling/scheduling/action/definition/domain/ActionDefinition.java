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
public class ActionDefinition {
    String schemaVersion;
    String actionKey;
    String version;
    String displayName;
    String description;
    boolean entryPoint;
    String scope;
    Map<String, ParameterSchema> inputSchema;
    Map<String, ParameterSchema> outputSchema;
    List<ActionStepDefinition> steps;
    ActionDefaultPolicy defaultPolicy;
    Map<String, String> labels;
    @ConstructorProperties({"schemaVersion", "actionKey", "version", "displayName", "description", "entryPoint", "scope", "inputSchema", "outputSchema", "steps", "defaultPolicy", "labels"})
    public ActionDefinition(
            String schemaVersion,
            String actionKey,
            String version,
            String displayName,
            String description,
            boolean entryPoint,
            String scope,
            Map<String, ParameterSchema> inputSchema,
            Map<String, ParameterSchema> outputSchema,
            List<ActionStepDefinition> steps,
            ActionDefaultPolicy defaultPolicy,
            Map<String, String> labels
    ) {
        schemaVersion = schemaVersion == null || schemaVersion.trim().isEmpty() ? "1.0" : schemaVersion;
        description = description == null ? "" : description;
        scope = scope == null || scope.trim().isEmpty() ? "TIANJIN" : scope;
        inputSchema = inputSchema == null ? ImmutableCollections.mapOf() : ImmutableCollections.copyMap(new LinkedHashMap<>(inputSchema));
        outputSchema = outputSchema == null ? ImmutableCollections.mapOf() : ImmutableCollections.copyMap(new LinkedHashMap<>(outputSchema));
        steps = steps == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(steps);
        defaultPolicy = defaultPolicy == null ? new ActionDefaultPolicy(60_000, FailurePolicy.abort()) : defaultPolicy;
        labels = labels == null ? ImmutableCollections.mapOf() : ImmutableCollections.copyMap(new LinkedHashMap<>(labels));
        this.schemaVersion = schemaVersion;
        this.actionKey = actionKey;
        this.version = version;
        this.displayName = displayName;
        this.description = description;
        this.entryPoint = entryPoint;
        this.scope = scope;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.steps = steps;
        this.defaultPolicy = defaultPolicy;
        this.labels = labels;
    }
}
