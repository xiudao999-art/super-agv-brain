package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 下发前只读预览，同时也是创建执行快照的完整输入。 */
@Schema(description = "下发前完整动作包只读预览")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionPackagePreview {
    @Schema(description = "Action 唯一标识")
    String actionKey;
    @Schema(description = "执行时使用的 Action revision")
    long actionRevision;
    @Schema(description = "下游主动作类型")
    String downstreamActionType;
    @Schema(description = "联调参数集标识")
    String parameterSetId;
    @Schema(description = "联调参数集 revision")
    Long parameterSetRevision;
    @Schema(description = "cnet8 线协议兼容号，不是业务 Action 版本")
    String protocolActionVersion;
    @Schema(description = "最终动作包哈希；正式执行时必须原样回传")
    String packageHash;
    @Schema(description = "整个 Action 超时时间，单位毫秒")
    int timeoutMs;
    @Schema(description = "执行时冻结的 Action 定义快照")
    JsonNode definitionSnapshot;
    @Schema(description = "执行时冻结的联调参数快照")
    JsonNode parameterSnapshot;
    @Schema(description = "下发给 cnet8 的 MainAction 完整内容")
    JsonNode commandInput;
    @Schema(description = "按顺序解析后的阶段及最终参数")
    JsonNode resolvedSteps;

    @ConstructorProperties({"actionKey", "actionRevision", "downstreamActionType", "parameterSetId",
            "parameterSetRevision", "protocolActionVersion", "packageHash", "timeoutMs",
            "definitionSnapshot", "parameterSnapshot", "commandInput", "resolvedSteps"})
    public ActionPackagePreview(String actionKey,
                                long actionRevision,
                                String downstreamActionType,
                                String parameterSetId,
                                Long parameterSetRevision,
                                String protocolActionVersion,
                                String packageHash,
                                int timeoutMs,
                                JsonNode definitionSnapshot,
                                JsonNode parameterSnapshot,
                                JsonNode commandInput,
                                JsonNode resolvedSteps) {
        this.actionKey = actionKey;
        this.actionRevision = actionRevision;
        this.downstreamActionType = downstreamActionType;
        this.parameterSetId = parameterSetId;
        this.parameterSetRevision = parameterSetRevision;
        this.protocolActionVersion = protocolActionVersion;
        this.packageHash = packageHash;
        this.timeoutMs = timeoutMs;
        this.definitionSnapshot = definitionSnapshot;
        this.parameterSnapshot = parameterSnapshot;
        this.commandInput = commandInput;
        this.resolvedSteps = resolvedSteps;
    }
}
