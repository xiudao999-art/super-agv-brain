package com.kunling.scheduling.action.compilation.domain;

import com.kunling.scheduling.action.shared.ImmutableCollections;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kunling.scheduling.action.definition.domain.FailurePolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties("capabilityVersion")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ExecutionNode {
    String executionNodeId;
    String stepId;
    String displayName;
    String sourcePath;
    String sourceActionKey;
    String sourceActionVersion;
    List<ActionGroupReference> groups;
    List<LoopFrame> loops;
    List<ConditionGuard> guards;
    String capabilityKey;
    String capabilityContractHash;
    Map<String, JsonNode> bindings;
    int timeoutMs;
    FailurePolicy onFailure;
    boolean gate;
    List<String> resources;
    boolean hasPhysicalSideEffect;
    @ConstructorProperties({"executionNodeId", "stepId", "displayName", "sourcePath", "sourceActionKey", "sourceActionVersion", "groups", "loops", "guards", "capabilityKey", "capabilityContractHash", "bindings", "timeoutMs", "onFailure", "gate", "resources", "hasPhysicalSideEffect"})
    public ExecutionNode(
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
            boolean hasPhysicalSideEffect
    ) {
        groups = groups == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(groups);
        loops = loops == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(loops);
        guards = guards == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(guards);
        bindings = bindings == null ? ImmutableCollections.mapOf() : ImmutableCollections.copyMap(new LinkedHashMap<>(bindings));
        onFailure = onFailure == null ? FailurePolicy.abort() : onFailure;
        resources = resources == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(resources);
        this.executionNodeId = executionNodeId;
        this.stepId = stepId;
        this.displayName = displayName;
        this.sourcePath = sourcePath;
        this.sourceActionKey = sourceActionKey;
        this.sourceActionVersion = sourceActionVersion;
        this.groups = groups;
        this.loops = loops;
        this.guards = guards;
        this.capabilityKey = capabilityKey;
        this.capabilityContractHash = capabilityContractHash;
        this.bindings = bindings;
        this.timeoutMs = timeoutMs;
        this.onFailure = onFailure;
        this.gate = gate;
        this.resources = resources;
        this.hasPhysicalSideEffect = hasPhysicalSideEffect;
    }

    public ExecutionNode materialized(Map<String, JsonNode> materializedBindings) {
        return new ExecutionNode(executionNodeId, stepId, displayName, sourcePath, sourceActionKey,
                sourceActionVersion, groups, ImmutableCollections.listOf(), ImmutableCollections.listOf(), capabilityKey, capabilityContractHash,
                materializedBindings, timeoutMs, onFailure, gate, resources, hasPhysicalSideEffect);
    }
}
