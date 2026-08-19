package com.kunling.scheduling.action.definition.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.compilation.domain.ActionDependency;
import com.kunling.scheduling.action.compilation.domain.CapabilityRequirement;
import com.kunling.scheduling.action.compilation.domain.ExecutionPlan;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionReleaseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionReleaseView {
    UUID id;
    String actionKey;
    String actionVersion;
    String compilerVersion;
    ActionDefinition definition;
    ExecutionPlan plan;
    String canonicalJson;
    String planHash;
    List<CapabilityRequirement> requiredCapabilities;
    List<ActionDependency> dependencies;
    String changeSummary;
    ActionReleaseStatus status;
    Instant publishedAt;
    Instant deprecatedAt;
    @ConstructorProperties({"id", "actionKey", "actionVersion", "compilerVersion", "definition", "plan", "canonicalJson", "planHash", "requiredCapabilities", "dependencies", "changeSummary", "status", "publishedAt", "deprecatedAt"})
    public ActionReleaseView(
            UUID id,
            String actionKey,
            String actionVersion,
            String compilerVersion,
            ActionDefinition definition,
            ExecutionPlan plan,
            String canonicalJson,
            String planHash,
            List<CapabilityRequirement> requiredCapabilities,
            List<ActionDependency> dependencies,
            String changeSummary,
            ActionReleaseStatus status,
            Instant publishedAt,
            Instant deprecatedAt
    ) {
        this.id = id;
        this.actionKey = actionKey;
        this.actionVersion = actionVersion;
        this.compilerVersion = compilerVersion;
        this.definition = definition;
        this.plan = plan;
        this.canonicalJson = canonicalJson;
        this.planHash = planHash;
        this.requiredCapabilities = requiredCapabilities;
        this.dependencies = dependencies;
        this.changeSummary = changeSummary;
        this.status = status;
        this.publishedAt = publishedAt;
        this.deprecatedAt = deprecatedAt;
    }

}
