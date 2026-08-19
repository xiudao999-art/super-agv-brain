package com.kunling.scheduling.action.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.definition.domain.ActionReleaseStatus;
import com.kunling.scheduling.action.definition.domain.ParameterSchema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionCatalogItem {
    String actionKey;
    String version;
    String displayName;
    String description;
    String scope;
    boolean entryPoint;
    Map<String, ParameterSchema> inputSchema;
    Map<String, String> labels;
    boolean hasPhysicalSideEffect;
    List<AtomicStep> atomicSteps;
    int compiledNodeCount;
    int dependencyCount;
    int defaultTimeoutMs;
    ActionReleaseStatus status;
    Instant publishedAt;
    @ConstructorProperties({"actionKey", "version", "displayName", "description", "scope", "entryPoint", "inputSchema", "labels", "hasPhysicalSideEffect", "atomicSteps", "compiledNodeCount", "dependencyCount", "defaultTimeoutMs", "status", "publishedAt"})
    public ActionCatalogItem(
            String actionKey,
            String version,
            String displayName,
            String description,
            String scope,
            boolean entryPoint,
            Map<String, ParameterSchema> inputSchema,
            Map<String, String> labels,
            boolean hasPhysicalSideEffect,
            List<AtomicStep> atomicSteps,
            int compiledNodeCount,
            int dependencyCount,
            int defaultTimeoutMs,
            ActionReleaseStatus status,
            Instant publishedAt
    ) {
        this.actionKey = actionKey;
        this.version = version;
        this.displayName = displayName;
        this.description = description;
        this.scope = scope;
        this.entryPoint = entryPoint;
        this.inputSchema = inputSchema;
        this.labels = labels;
        this.hasPhysicalSideEffect = hasPhysicalSideEffect;
        this.atomicSteps = atomicSteps;
        this.compiledNodeCount = compiledNodeCount;
        this.dependencyCount = dependencyCount;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.status = status;
        this.publishedAt = publishedAt;
    }


    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class AtomicStep {
        String stepId;
        String displayName;
        String capabilityKey;
        String capabilityContractHash;
        int depth;
        @ConstructorProperties({"stepId", "displayName", "capabilityKey", "capabilityContractHash", "depth"})
        public AtomicStep(
                String stepId,
                String displayName,
                String capabilityKey,
                String capabilityContractHash,
                int depth
        ) {
            this.stepId = stepId;
            this.displayName = displayName;
            this.capabilityKey = capabilityKey;
            this.capabilityContractHash = capabilityContractHash;
            this.depth = depth;
        }

    }
}
