package com.kunling.scheduling.action.upstream.application;

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
public record AtomicCapabilityDescriptor(
        String capabilityKey,
        Map<String, ParameterSchema> inputSchema,
        Map<String, ParameterSchema> outputSchema,
        List<String> resources,
        CapabilitySideEffect sideEffect,
        CapabilityRetrySafety retrySafety,
        boolean safetyCritical,
        boolean requiresMotionSafetyParameters) {

    public AtomicCapabilityDescriptor {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(inputSchema));
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(outputSchema));
        resources = resources == null ? List.of() : List.copyOf(resources);
        sideEffect = sideEffect == null ? CapabilitySideEffect.NONE : sideEffect;
        retrySafety = retrySafety == null ? CapabilityRetrySafety.NEVER : retrySafety;
    }

    public CapabilityManifest toManifest(String contractHash) {
        return new CapabilityManifest(capabilityKey, contractHash, inputSchema, outputSchema, resources,
                sideEffect, retrySafety, safetyCritical, requiresMotionSafetyParameters);
    }
}
