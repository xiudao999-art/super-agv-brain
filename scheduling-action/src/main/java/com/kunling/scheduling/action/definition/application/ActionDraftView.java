package com.kunling.scheduling.action.definition.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionDraftStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Schema(description = "可编辑的动作草稿")
public class ActionDraftView {
    @Schema(description = "动作草稿唯一标识")
    UUID id;
    @Schema(description = "动作编码")
    String actionKey;
    @Schema(description = "草稿修订号，用于并发更新控制")
    long revision;
    @Schema(description = "完整动作定义")
    ActionDefinition definition;
    @Schema(description = "草稿状态")
    ActionDraftStatus status;
    @Schema(description = "草稿创建时间")
    Instant createdAt;
    @Schema(description = "草稿最近更新时间")
    Instant updatedAt;
    @ConstructorProperties({"id", "actionKey", "revision", "definition", "status", "createdAt", "updatedAt"})
    public ActionDraftView(
            UUID id,
            String actionKey,
            long revision,
            ActionDefinition definition,
            ActionDraftStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.actionKey = actionKey;
        this.revision = revision;
        this.definition = definition;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

}
