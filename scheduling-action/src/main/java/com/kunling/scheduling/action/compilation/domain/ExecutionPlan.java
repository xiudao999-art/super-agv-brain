package com.kunling.scheduling.action.compilation.domain;

import com.kunling.scheduling.action.shared.ImmutableCollections;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import java.util.List;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ExecutionPlan {
    String schemaVersion;
    String compilerVersion;
    String actionKey;
    String actionVersion;
    String planHash;
    List<ExecutionNode> nodes;
    List<CapabilityRequirement> requiredCapabilities;
    List<ActionDependency> dependencies;
    int compiledNodeCount;
    int maxExpandedNodeCount;
    @ConstructorProperties({"schemaVersion", "compilerVersion", "actionKey", "actionVersion", "planHash", "nodes", "requiredCapabilities", "dependencies", "compiledNodeCount", "maxExpandedNodeCount"})
    public ExecutionPlan(
            String schemaVersion,
            String compilerVersion,
            String actionKey,
            String actionVersion,
            String planHash,
            List<ExecutionNode> nodes,
            List<CapabilityRequirement> requiredCapabilities,
            List<ActionDependency> dependencies,
            int compiledNodeCount,
            int maxExpandedNodeCount
    ) {
        schemaVersion = schemaVersion == null ? "1.0" : schemaVersion;
        nodes = nodes == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(nodes);
        requiredCapabilities = requiredCapabilities == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(requiredCapabilities);
        dependencies = dependencies == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(dependencies);
        this.schemaVersion = schemaVersion;
        this.compilerVersion = compilerVersion;
        this.actionKey = actionKey;
        this.actionVersion = actionVersion;
        this.planHash = planHash;
        this.nodes = nodes;
        this.requiredCapabilities = requiredCapabilities;
        this.dependencies = dependencies;
        this.compiledNodeCount = compiledNodeCount;
        this.maxExpandedNodeCount = maxExpandedNodeCount;
    }

    public ExecutionPlan withPlanHash(String hash) {
        return new ExecutionPlan(schemaVersion, compilerVersion, actionKey, actionVersion, hash,
                nodes, requiredCapabilities, dependencies, compiledNodeCount, maxExpandedNodeCount);
    }
}
