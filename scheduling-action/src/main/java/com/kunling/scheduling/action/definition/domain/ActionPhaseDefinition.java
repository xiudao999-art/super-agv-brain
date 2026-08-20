package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** Action 中一个有序执行阶段；相同 subAction 可以通过不同 phaseId 重复出现。 */
@Schema(description = "Action 中一个有序执行阶段")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionPhaseDefinition {

    @Schema(description = "阶段唯一标识", example = "move-to-pick")
    String phaseId;
    @Schema(description = "阶段中文名称", example = "移动至抓取位")
    String displayName;
    @Schema(description = "下游子动作协议值")
    DownstreamSubAction subAction;
    @Schema(description = "是否执行该阶段")
    boolean enabled;
    @Schema(description = "子动作最终参数；支持 $input.* 和 $parameters.* 绑定")
    @JsonProperty("params") JsonNode parameters;
    @Schema(description = "是否为验收门禁；门禁阶段不能配置跳过失败")
    boolean gate;
    @Schema(description = "阶段失败时的业务策略")
    PhaseFailureAction onFail;
    @Schema(description = "业务层最大重试次数", example = "1")
    int maxRetries;
    @Schema(description = "重试前回退到的前置阶段；不回退时为空")
    String retryFromPhaseId;
    @Schema(description = "重试次数耗尽后的处理策略")
    RetryExhaustedAction onExhaust;

    @ConstructorProperties({"phaseId", "displayName", "subAction", "enabled", "params", "gate",
            "onFail", "maxRetries", "retryFromPhaseId", "onExhaust"})
    public ActionPhaseDefinition(String phaseId,
                                 String displayName,
                                 DownstreamSubAction subAction,
                                 Boolean enabled,
                                 @JsonProperty("params") JsonNode parameters,
                                 boolean gate,
                                 PhaseFailureAction onFail,
                                 int maxRetries,
                                 String retryFromPhaseId,
                                 RetryExhaustedAction onExhaust) {
        this.phaseId = normalize(phaseId);
        this.displayName = normalize(displayName);
        this.subAction = subAction;
        this.enabled = enabled == null || enabled;
        this.parameters = parameters == null ? JsonNodeFactory.instance.objectNode() : parameters.deepCopy();
        this.gate = gate;
        this.onFail = onFail == null ? PhaseFailureAction.ABORT : onFail;
        this.maxRetries = maxRetries;
        this.retryFromPhaseId = normalizeToNull(retryFromPhaseId);
        this.onExhaust = onExhaust == null ? RetryExhaustedAction.HOLD : onExhaust;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
