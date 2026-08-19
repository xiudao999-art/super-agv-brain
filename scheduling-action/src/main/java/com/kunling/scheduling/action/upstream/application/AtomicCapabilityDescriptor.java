package com.kunling.scheduling.action.upstream.application;

import com.kunling.scheduling.action.shared.ImmutableCollections;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.capability.domain.CapabilityManifest;
import com.kunling.scheduling.action.capability.domain.CapabilityRetrySafety;
import com.kunling.scheduling.action.capability.domain.CapabilitySideEffect;
import com.kunling.scheduling.action.definition.domain.ParameterSchema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 上游当前可用的原子能力描述；契约 Hash 由下游同步时统一计算。 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class AtomicCapabilityDescriptor {
    String capabilityKey;
    Map<String, ParameterSchema> inputSchema;
    Map<String, ParameterSchema> outputSchema;
    List<String> resources;
    CapabilitySideEffect sideEffect;
    CapabilityRetrySafety retrySafety;
    boolean safetyCritical;
    boolean requiresMotionSafetyParameters;
    @ConstructorProperties({"capabilityKey", "inputSchema", "outputSchema", "resources", "sideEffect", "retrySafety", "safetyCritical", "requiresMotionSafetyParameters"})
    public AtomicCapabilityDescriptor(
            String capabilityKey,
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
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.resources = resources;
        this.sideEffect = sideEffect;
        this.retrySafety = retrySafety;
        this.safetyCritical = safetyCritical;
        this.requiresMotionSafetyParameters = requiresMotionSafetyParameters;
    }

    public CapabilityManifest toManifest(String contractHash) {
        return new CapabilityManifest(capabilityKey, contractHash, inputSchema, outputSchema, resources,
                sideEffect, retrySafety, safetyCritical, requiresMotionSafetyParameters);
    }
}
