package com.kunling.scheduling.action.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public final class ActionRequests {

    private ActionRequests() {
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Schema(description = "保存动作草稿请求")
    public static class SaveDraftRequest {
        @Schema(description = "完整动作定义", required = true)
        ActionDefinition definition;
        @Schema(description = "草稿唯一标识；新建草稿时不填写")
        UUID draftId;
        @Schema(description = "期望的草稿修订号；更新已有草稿时用于并发控制")
        Long expectedRevision;
        @ConstructorProperties({"definition", "draftId", "expectedRevision"})
        public SaveDraftRequest(
                ActionDefinition definition,
                UUID draftId,
                Long expectedRevision
        ) {
            this.definition = definition;
            this.draftId = draftId;
            this.expectedRevision = expectedRevision;
        }

    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Schema(description = "基于发布版本创建新草稿请求")
    public static class CloneReleaseRequest {
        @Schema(description = "动作编码", example = "TIANJIN_PICK_AND_PLACE", required = true)
        String actionKey;
        @Schema(description = "需要复制的源版本", example = "1.0.0", required = true)
        String sourceVersion;
        @Schema(description = "新草稿使用的目标版本，不能与已有草稿或发布版本重复", example = "1.1.0", required = true)
        String newVersion;
        @ConstructorProperties({"actionKey", "sourceVersion", "newVersion"})
        public CloneReleaseRequest(
                String actionKey,
                String sourceVersion,
                String newVersion
        ) {
            this.actionKey = actionKey;
            this.sourceVersion = sourceVersion;
            this.newVersion = newVersion;
        }

    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Schema(description = "发布动作草稿请求")
    public static class PublishDraftRequest {
        @Schema(description = "本次发布的中文变更说明", example = "调整取料等待时间", required = true)
        String changeSummary;
        @ConstructorProperties({"changeSummary"})
        public PublishDraftRequest(
                String changeSummary
        ) {
            this.changeSummary = changeSummary;
        }

    }
}
