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
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Schema(description = "不可变的动作发布版本")
public class ActionReleaseView {
    @Schema(description = "发布记录唯一标识")
    UUID id;
    @Schema(description = "动作编码")
    String actionKey;
    @Schema(description = "动作版本")
    String actionVersion;
    @Schema(description = "生成执行计划的编译器版本")
    String compilerVersion;
    @Schema(description = "发布时固化的动作定义")
    ActionDefinition definition;
    @Schema(description = "发布时固化的执行计划")
    ExecutionPlan plan;
    @Schema(description = "动作定义的规范化 JSON")
    String canonicalJson;
    @Schema(description = "执行计划内容哈希")
    String planHash;
    @Schema(description = "执行所需的原子能力契约")
    List<CapabilityRequirement> requiredCapabilities;
    @Schema(description = "精确版本动作依赖")
    List<ActionDependency> dependencies;
    @Schema(description = "发布变更说明")
    String changeSummary;
    @Schema(description = "发布版本状态")
    ActionReleaseStatus status;
    @Schema(description = "发布时间")
    Instant publishedAt;
    @Schema(description = "停用时间；仍有效时为空")
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
