package com.kunling.scheduling.action.definition.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ActionDefinition(
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
        Map<String, String> labels) {

    public ActionDefinition {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion;
        description = description == null ? "" : description;
        scope = scope == null || scope.isBlank() ? "TIANJIN" : scope;
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(inputSchema));
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(outputSchema));
        steps = steps == null ? List.of() : List.copyOf(steps);
        defaultPolicy = defaultPolicy == null ? new ActionDefaultPolicy(60_000, FailurePolicy.abort()) : defaultPolicy;
        labels = labels == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(labels));
    }
}
