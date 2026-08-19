package com.kunling.scheduling.action.compilation.domain;

import java.util.List;

public record CompileResult(
        boolean success,
        List<CompileIssue> issues,
        ExecutionPlan plan,
        List<CapabilityRequirement> requiredCapabilities,
        List<ActionDependency> dependencies,
        String canonicalJson,
        String planHash,
        String compilerVersion) {

    public CompileResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
