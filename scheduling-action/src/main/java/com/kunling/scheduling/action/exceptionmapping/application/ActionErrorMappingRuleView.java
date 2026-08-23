package com.kunling.scheduling.action.exceptionmapping.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.kunling.scheduling.action.exceptionmapping.domain.ActionErrorMappingRule;
import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.time.Instant;

@Schema(description = "厂家异常映射规则详情")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionErrorMappingRuleView {
    String ruleId;
    String profileId;
    long revision;
    ErrorMappingRuleStatus status;
    ActionErrorMappingRule rule;
    Instant createdAt;
    Instant updatedAt;
}
