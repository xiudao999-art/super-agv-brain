package com.kunling.scheduling.action.interfaces.rest;

import com.kunling.scheduling.action.definition.domain.ActionReleaseStatus;
import com.kunling.scheduling.action.definition.domain.ParameterSchema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ActionCatalogItem(
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
        Instant publishedAt) {

    public record AtomicStep(
            String stepId,
            String displayName,
            String capabilityKey,
            String capabilityContractHash,
            int depth) {
    }
}
