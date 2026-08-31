package com.kunling.scheduling.action.exceptionmapping.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 厂家原始异常映射后的稳定业务事实，不包含运行时物理结果。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ErrorMappingRuleResult {
    String businessCode;
    String businessMessage;
    String reasonCode;
    HandlingConstraint handlingConstraint;
    String handlingAdvice;

    @ConstructorProperties({"businessCode", "businessMessage", "reasonCode",
            "handlingConstraint", "handlingAdvice"})
    public ErrorMappingRuleResult(String businessCode, String businessMessage,
                                  String reasonCode, HandlingConstraint handlingConstraint,
                                  String handlingAdvice) {
        this.businessCode = normalize(businessCode);
        this.businessMessage = normalize(businessMessage);
        this.reasonCode = normalize(reasonCode);
        this.handlingConstraint = handlingConstraint;
        this.handlingAdvice = normalizeToNull(handlingAdvice);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
