package com.kunling.scheduling.action.exceptionmapping.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 厂家异常命中后生成的稳定业务语义。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ErrorMappingRuleResult {
    String businessCode;
    String businessMessage;
    String reasonCode;
    BusinessDisposition businessDisposition;
    PhysicalOutcome physicalOutcome;
    String handlingAdvice;

    @ConstructorProperties({"businessCode", "businessMessage", "reasonCode", "businessDisposition",
            "physicalOutcome", "handlingAdvice"})
    public ErrorMappingRuleResult(String businessCode,
                                  String businessMessage,
                                  String reasonCode,
                                  BusinessDisposition businessDisposition,
                                  PhysicalOutcome physicalOutcome,
                                  String handlingAdvice) {
        this.businessCode = normalize(businessCode);
        this.businessMessage = normalize(businessMessage);
        this.reasonCode = normalize(reasonCode);
        this.businessDisposition = businessDisposition;
        this.physicalOutcome = physicalOutcome;
        this.handlingAdvice = normalize(handlingAdvice);
    }

    /** 兼容尚未显式提供标准业务消息的旧调用方。 */
    public ErrorMappingRuleResult(String businessCode,
                                  String reasonCode,
                                  BusinessDisposition businessDisposition,
                                  PhysicalOutcome physicalOutcome,
                                  String handlingAdvice) {
        this(businessCode, reasonCode, reasonCode, businessDisposition, physicalOutcome,
                handlingAdvice);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
