package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kunling.scheduling.action.config.ImmutableCollections;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 当前生效语义的 Action 定义。
 *
 * <p>Action 不再维护业务版本。每次执行开始前会把本对象和设备联调参数集物化为
 * 不可变执行快照。只要该 Action 仍有运行实例，模板编辑会被拒绝；全部执行结束后才允许调整。</p>
 */
@Schema(description = "当前 Action 的动态配置定义")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
// 仅兼容迁移脚本执行前数据库中已有的定义 JSON；当前 API 模型不再暴露 inputSchema。
@JsonIgnoreProperties(value = "inputSchema")
public class ActionDefinition {

    @Schema(description = "定义结构版本，仅用于 JSON 结构兼容", example = "1.0")
    String schemaVersion;
    @Schema(description = "Action 唯一标识", example = "ARM.PICK")
    String actionKey;
    @Schema(description = "下游主动作类型，协议值保持英文")
    DownstreamActionType downstreamActionType;
    @Schema(description = "页面显示名称", example = "机械臂抓取")
    String displayName;
    @Schema(description = "用途及适用场景说明")
    String description;
    @Schema(description = "设备联调参数约束，是 Action 唯一的运行参数来源，键为参数名")
    Map<String, ParameterSchema> parameterSchema;
    @Schema(description = "按顺序执行的子动作阶段，可重复使用同一种子动作")
    List<ActionPhaseDefinition> phases;
    @Schema(description = "整个 Action 超时时间，单位毫秒", example = "90000")
    int timeoutMs;

    @ConstructorProperties({"schemaVersion", "actionKey", "downstreamActionType", "displayName",
            "description", "parameterSchema", "phases", "timeoutMs"})
    public ActionDefinition(String schemaVersion,
                            String actionKey,
                            DownstreamActionType downstreamActionType,
                            String displayName,
                            String description,
                            Map<String, ParameterSchema> parameterSchema,
                            List<ActionPhaseDefinition> phases,
                            int timeoutMs) {
        this.schemaVersion = isBlank(schemaVersion) ? "1.0" : schemaVersion.trim();
        this.actionKey = normalize(actionKey);
        this.downstreamActionType = downstreamActionType;
        this.displayName = normalize(displayName);
        this.description = description == null ? "" : description.trim();
        this.parameterSchema = parameterSchema == null
                ? ImmutableCollections.mapOf()
                : ImmutableCollections.copyMap(new LinkedHashMap<String, ParameterSchema>(parameterSchema));
        this.phases = phases == null
                ? ImmutableCollections.listOf()
                : ImmutableCollections.copyList(phases);
        this.timeoutMs = timeoutMs <= 0 ? 60_000 : timeoutMs;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
