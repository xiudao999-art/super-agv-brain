package com.kunling.scheduling.action.compilation.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kunling.scheduling.action.definition.domain.FailurePolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties("capabilityVersion")
public record ExecutionNode(
        String executionNodeId,
        String stepId,
        String displayName,
        String sourcePath,
        String sourceActionKey,
        String sourceActionVersion,
        List<ActionGroupReference> groups,
        List<LoopFrame> loops,
        List<ConditionGuard> guards,
        String capabilityKey,
        String capabilityContractHash,
        Map<String, JsonNode> bindings,
        int timeoutMs,
        FailurePolicy onFailure,
        boolean gate,
        List<String> resources,
        boolean hasPhysicalSideEffect) {

    public ExecutionNode {
        groups = groups == null ? List.of() : List.copyOf(groups);
        loops = loops == null ? List.of() : List.copyOf(loops);
        guards = guards == null ? List.of() : List.copyOf(guards);
        bindings = bindings == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(bindings));
        onFailure = onFailure == null ? FailurePolicy.abort() : onFailure;
        resources = resources == null ? List.of() : List.copyOf(resources);
    }

    public ExecutionNode materialized(Map<String, JsonNode> materializedBindings) {
        return new ExecutionNode(executionNodeId, stepId, displayName, sourcePath, sourceActionKey,
                sourceActionVersion, groups, List.of(), List.of(), capabilityKey, capabilityContractHash,
                materializedBindings, timeoutMs, onFailure, gate, resources, hasPhysicalSideEffect);
    }
}
