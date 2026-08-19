package com.kunling.scheduling.action.capability.domain;

import com.kunling.scheduling.action.shared.ImmutableCollections;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.definition.domain.ParameterSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class CapabilityManifest {
    String capabilityKey;
    String contractHash;
    Map<String, ParameterSchema> inputSchema;
    Map<String, ParameterSchema> outputSchema;
    List<String> resources;
    CapabilitySideEffect sideEffect;
    CapabilityRetrySafety retrySafety;
    boolean safetyCritical;
    boolean requiresMotionSafetyParameters;
    @ConstructorProperties({"capabilityKey", "contractHash", "inputSchema", "outputSchema", "resources", "sideEffect", "retrySafety", "safetyCritical", "requiresMotionSafetyParameters"})
    public CapabilityManifest(
            String capabilityKey,
            String contractHash,
            Map<String, ParameterSchema> inputSchema,
            Map<String, ParameterSchema> outputSchema,
            List<String> resources,
            CapabilitySideEffect sideEffect,
            CapabilityRetrySafety retrySafety,
            boolean safetyCritical,
            boolean requiresMotionSafetyParameters
    ) {
        inputSchema = inputSchema == null ? ImmutableCollections.mapOf() : ImmutableCollections.copyMap(new LinkedHashMap<>(inputSchema));
        outputSchema = outputSchema == null ? ImmutableCollections.mapOf() : ImmutableCollections.copyMap(new LinkedHashMap<>(outputSchema));
        resources = resources == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(resources);
        sideEffect = sideEffect == null ? CapabilitySideEffect.NONE : sideEffect;
        retrySafety = retrySafety == null ? CapabilityRetrySafety.NEVER : retrySafety;
        this.capabilityKey = capabilityKey;
        this.contractHash = contractHash;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.resources = resources;
        this.sideEffect = sideEffect;
        this.retrySafety = retrySafety;
        this.safetyCritical = safetyCritical;
        this.requiresMotionSafetyParameters = requiresMotionSafetyParameters;
    }

    public String identity() {
        return capabilityKey;
    }
}
