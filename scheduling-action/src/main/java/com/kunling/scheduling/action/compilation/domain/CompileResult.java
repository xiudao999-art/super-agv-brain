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
public class CompileResult {
    boolean success;
    List<CompileIssue> issues;
    ExecutionPlan plan;
    List<CapabilityRequirement> requiredCapabilities;
    List<ActionDependency> dependencies;
    String canonicalJson;
    String planHash;
    String compilerVersion;
    @ConstructorProperties({"success", "issues", "plan", "requiredCapabilities", "dependencies", "canonicalJson", "planHash", "compilerVersion"})
    public CompileResult(
            boolean success,
            List<CompileIssue> issues,
            ExecutionPlan plan,
            List<CapabilityRequirement> requiredCapabilities,
            List<ActionDependency> dependencies,
            String canonicalJson,
            String planHash,
            String compilerVersion
    ) {
        issues = issues == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(issues);
        requiredCapabilities = requiredCapabilities == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(requiredCapabilities);
        dependencies = dependencies == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(dependencies);
        this.success = success;
        this.issues = issues;
        this.plan = plan;
        this.requiredCapabilities = requiredCapabilities;
        this.dependencies = dependencies;
        this.canonicalJson = canonicalJson;
        this.planHash = planHash;
        this.compilerVersion = compilerVersion;
    }
}
