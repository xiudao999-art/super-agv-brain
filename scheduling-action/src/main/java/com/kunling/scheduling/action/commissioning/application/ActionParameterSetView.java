package com.kunling.scheduling.action.commissioning.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.time.Instant;

/** 某个 Action 在具体机器人、工装和物料上的当前联调参数。 */
@Schema(description = "设备联调参数集详情")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionParameterSetView {
    @Schema(description = "联调参数集标识")
    String id;
    @Schema(description = "所属 Action 标识", example = "ARM.PICK")
    String actionKey;
    @Schema(description = "参数集名称")
    String name;
    @Schema(description = "适用机器人标识")
    String robotId;
    @Schema(description = "适用工装标识")
    String fixtureKey;
    @Schema(description = "适用物料标识")
    String materialKey;
    @Schema(description = "详细设备参数")
    JsonNode values;
    @Schema(description = "并发控制 revision，不是业务版本", example = "1")
    long revision;
    @Schema(description = "是否允许用于新执行任务")
    boolean enabled;
    @Schema(description = "是否被执行中的任务锁定")
    boolean executionLocked;
    @Schema(description = "活动执行实例标识；未锁定时为空")
    String activeExecutionId;
    @Schema(description = "创建时间")
    Instant createdAt;
    @Schema(description = "最后修改时间")
    Instant updatedAt;

    @ConstructorProperties({"id", "actionKey", "name", "robotId", "fixtureKey", "materialKey",
            "values", "revision", "enabled", "executionLocked", "activeExecutionId",
            "createdAt", "updatedAt"})
    public ActionParameterSetView(String id,
                                  String actionKey,
                                  String name,
                                  String robotId,
                                  String fixtureKey,
                                  String materialKey,
                                  JsonNode values,
                                  long revision,
                                  boolean enabled,
                                  boolean executionLocked,
                                  String activeExecutionId,
                                  Instant createdAt,
                                  Instant updatedAt) {
        this.id = id;
        this.actionKey = actionKey;
        this.name = name;
        this.robotId = robotId;
        this.fixtureKey = fixtureKey;
        this.materialKey = materialKey;
        this.values = values;
        this.revision = revision;
        this.enabled = enabled;
        this.executionLocked = executionLocked;
        this.activeExecutionId = activeExecutionId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
