package com.kunling.scheduling.action.exceptionmapping.application;

import com.kunling.scheduling.action.exceptionmapping.domain.HandlingConstraint;
import lombok.Value;
import lombok.experimental.Accessors;

/** 厂家原始异常映射后的业务事实。 */
@Value
@Accessors(fluent = true)
public class BusinessErrorDecision {
    String businessCode;
    String businessMessage;
    String reasonCode;
    HandlingConstraint handlingConstraint;
    String matchedRuleId;
    String mappingProfileId;
    String handlingAdvice;
}
