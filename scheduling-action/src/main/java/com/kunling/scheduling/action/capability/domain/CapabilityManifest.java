package com.kunling.scheduling.action.capability.domain;

import com.kunling.scheduling.action.definition.domain.ParameterSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CapabilityManifest(
        String capabilityKey,
        String contractHash,
        Map<String, ParameterSchema> inputSchema,
        Map<String, ParameterSchema> outputSchema,
        List<String> resources,
        CapabilitySideEffect sideEffect,
        CapabilityRetrySafety retrySafety,
        boolean safetyCritical,
        boolean requiresMotionSafetyParameters) {

    public CapabilityManifest {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(inputSchema));
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(outputSchema));
        resources = resources == null ? List.of() : List.copyOf(resources);
        sideEffect = sideEffect == null ? CapabilitySideEffect.NONE : sideEffect;
        retrySafety = retrySafety == null ? CapabilityRetrySafety.NEVER : retrySafety;
    }

    public String identity() {
        return capabilityKey;
    }
}
