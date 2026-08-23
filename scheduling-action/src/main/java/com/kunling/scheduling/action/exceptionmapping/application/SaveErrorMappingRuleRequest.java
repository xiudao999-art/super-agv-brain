package com.kunling.scheduling.action.exceptionmapping.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.kunling.scheduling.action.exceptionmapping.domain.ActionErrorMappingRule;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 保存厂家异常映射规则；revision 只用于防止陈旧请求覆盖。 */
@Schema(description = "保存厂家异常映射规则请求")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class SaveErrorMappingRuleRequest {
    @Schema(description = "当前技术 revision；新建时为空，修改时必填", example = "1")
    Long expectedRevision;
    @Schema(description = "完整映射规则")
    ActionErrorMappingRule rule;

    @ConstructorProperties({"expectedRevision", "rule"})
    public SaveErrorMappingRuleRequest(Long expectedRevision, ActionErrorMappingRule rule) {
        this.expectedRevision = expectedRevision;
        this.rule = rule;
    }
}
