package com.kunling.scheduling.action.definition.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 保存当前 Action 定义；expectedRevision 是并发控制号，不是业务版本。 */
@Schema(description = "保存 Action 定义请求")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class SaveActionDefinitionRequest {
    @Schema(description = "当前并发控制 revision；新建时为空，修改时必填", example = "1")
    Long expectedRevision;
    @Schema(description = "完整 Action 定义")
    ActionDefinition definition;

    @ConstructorProperties({"expectedRevision", "definition"})
    public SaveActionDefinitionRequest(Long expectedRevision, ActionDefinition definition) {
        this.expectedRevision = expectedRevision;
        this.definition = definition;
    }
}
