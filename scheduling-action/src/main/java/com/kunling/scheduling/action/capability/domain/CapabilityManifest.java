package com.kunling.scheduling.action.capability.domain;

import com.kunling.scheduling.action.shared.ImmutableCollections;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.definition.domain.ParameterSchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Schema(description = "上游发布并由下游保存的原子能力契约")
public class CapabilityManifest {
    @Schema(description = "原子能力唯一编码")
    String capabilityKey;
    @Schema(description = "能力契约内容哈希")
    String contractHash;
    @Schema(description = "输入参数 Schema")
    Map<String, ParameterSchema> inputSchema;
    @Schema(description = "输出参数 Schema")
    Map<String, ParameterSchema> outputSchema;
    @Schema(description = "执行时占用的互斥资源")
    List<String> resources;
    @Schema(description = "能力的物理副作用级别")
    CapabilitySideEffect sideEffect;
    @Schema(description = "能力的重试安全等级")
    CapabilityRetrySafety retrySafety;
    @Schema(description = "是否属于安全关键能力")
    boolean safetyCritical;
    @Schema(description = "是否必须提供运动安全参数")
    boolean requiresMotionSafetyParameters;
    @ConstructorProperties({"capabilityKey", "contractHash", "inputSchema", "outputSchema", "resources", "sideEffect", "retrySafety", "safetyCritical", "requiresMotionSafetyParameters"})
    public CapabilityManifest(
            String capabilityKey,
            String contractHash,
            Map<String, ParameterSchema> inputSchema,
            Map<String, ParameterSchema> outputSchema,
            List<String> resources,
            CapabilitySideEffect sideEffect,
            CapabilityRetrySafety retrySafety,
            boolean safetyCritical,
            boolean requiresMotionSafetyParameters
    ) {
        inputSchema = inputSchema == null ? ImmutableCollections.mapOf() : ImmutableCollections.copyMap(new LinkedHashMap<>(inputSchema));
        outputSchema = outputSchema == null ? ImmutableCollections.mapOf() : ImmutableCollections.copyMap(new LinkedHashMap<>(outputSchema));
        resources = resources == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(resources);
        sideEffect = sideEffect == null ? CapabilitySideEffect.NONE : sideEffect;
        retrySafety = retrySafety == null ? CapabilityRetrySafety.NEVER : retrySafety;
        this.capabilityKey = capabilityKey;
        this.contractHash = contractHash;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.resources = resources;
        this.sideEffect = sideEffect;
        this.retrySafety = retrySafety;
        this.safetyCritical = safetyCritical;
        this.requiresMotionSafetyParameters = requiresMotionSafetyParameters;
    }

    public String identity() {
        return capabilityKey;
    }
}
