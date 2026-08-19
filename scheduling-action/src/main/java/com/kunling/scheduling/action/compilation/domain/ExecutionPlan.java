package com.kunling.scheduling.action.compilation.domain;

import java.util.List;

public record ExecutionPlan(
        String schemaVersion,
        String compilerVersion,
        String actionKey,
        String actionVersion,
        String planHash,
        List<ExecutionNode> nodes,
        List<CapabilityRequirement> requiredCapabilities,
        List<ActionDependency> dependencies,
        int compiledNodeCount,
        int maxExpandedNodeCount) {

    public ExecutionPlan {
        schemaVersion = schemaVersion == null ? "1.0" : schemaVersion;
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    public ExecutionPlan withPlanHash(String hash) {
        return new ExecutionPlan(schemaVersion, compilerVersion, actionKey, actionVersion, hash,
                nodes, requiredCapabilities, dependencies, compiledNodeCount, maxExpandedNodeCount);
    }
}
