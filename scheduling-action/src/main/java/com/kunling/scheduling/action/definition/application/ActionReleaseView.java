package com.kunling.scheduling.action.definition.application;

import com.kunling.scheduling.action.compilation.domain.ActionDependency;
import com.kunling.scheduling.action.compilation.domain.CapabilityRequirement;
import com.kunling.scheduling.action.compilation.domain.ExecutionPlan;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionReleaseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActionReleaseView(
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
        Instant deprecatedAt) {
}
