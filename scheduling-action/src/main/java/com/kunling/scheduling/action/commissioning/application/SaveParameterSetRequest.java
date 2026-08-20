package com.kunling.scheduling.action.commissioning.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 保存设备联调参数集；revision 仅用于防止并发覆盖。 */
@Schema(description = "保存设备联调参数集请求")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class SaveParameterSetRequest {
    @Schema(description = "当前并发控制 revision；新建时为空，修改时必填", example = "1")
    Long expectedRevision;
    @Schema(description = "所属 Action 标识", example = "ARM.PICK")
    String actionKey;
    @Schema(description = "参数集名称", example = "天津一号线-A工位")
    String name;
    @Schema(description = "适用机器人标识；为空表示不限定机器人")
    String robotId;
    @Schema(description = "适用工装标识")
    String fixtureKey;
    @Schema(description = "适用物料标识")
    String materialKey;
    @Schema(description = "满足 Action parameterSchema 的详细设备参数")
    JsonNode values;
    @Schema(description = "是否允许用于新执行任务")
    boolean enabled;

    @ConstructorProperties({"expectedRevision", "actionKey", "name", "robotId", "fixtureKey",
            "materialKey", "values", "enabled"})
    public SaveParameterSetRequest(Long expectedRevision,
                                   String actionKey,
                                   String name,
                                   String robotId,
                                   String fixtureKey,
                                   String materialKey,
                                   JsonNode values,
                                   Boolean enabled) {
        this.expectedRevision = expectedRevision;
        this.actionKey = normalize(actionKey);
        this.name = normalize(name);
        this.robotId = normalizeToNull(robotId);
        this.fixtureKey = normalizeToNull(fixtureKey);
        this.materialKey = normalizeToNull(materialKey);
        this.values = values == null ? null : values.deepCopy();
        this.enabled = enabled == null || enabled;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
