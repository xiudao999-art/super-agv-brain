package com.kunling.scheduling.action.definition.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionDefinitionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.time.Instant;

/** Action 配置及其运行态写锁视图。 */
@Schema(description = "Action 配置详情")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionDefinitionView {
    @Schema(description = "数据库记录标识")
    String id;
    @Schema(description = "Action 唯一标识", example = "ARM.PICK")
    String actionKey;
    @Schema(description = "并发控制 revision，不是业务版本", example = "1")
    long revision;
    @Schema(description = "配置状态：DRAFT、ACTIVE 或 DISABLED")
    ActionDefinitionStatus status;
    @Schema(description = "完整 Action 定义")
    ActionDefinition definition;
    @Schema(description = "是否存在执行中的实例，锁定时禁止修改")
    boolean executionLocked;
    @Schema(description = "活动执行实例标识；未锁定时为空")
    String activeExecutionId;
    @Schema(description = "创建时间")
    Instant createdAt;
    @Schema(description = "最后修改时间")
    Instant updatedAt;

    @ConstructorProperties({"id", "actionKey", "revision", "status", "definition",
            "executionLocked", "activeExecutionId", "createdAt", "updatedAt"})
    public ActionDefinitionView(String id,
                                String actionKey,
                                long revision,
                                ActionDefinitionStatus status,
                                ActionDefinition definition,
                                boolean executionLocked,
                                String activeExecutionId,
                                Instant createdAt,
                                Instant updatedAt) {
        this.id = id;
        this.actionKey = actionKey;
        this.revision = revision;
        this.status = status;
        this.definition = definition;
        this.executionLocked = executionLocked;
        this.activeExecutionId = activeExecutionId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
