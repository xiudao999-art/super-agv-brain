package com.kunling.scheduling.action.exceptionmapping.application;

import com.kunling.scheduling.action.exceptionmapping.domain.BusinessDisposition;
import com.kunling.scheduling.action.exceptionmapping.domain.PhysicalOutcome;
import lombok.Value;
import lombok.experimental.Accessors;

/** 映射引擎输出；既能驱动作包策略，也能生成状态机最终报告。 */
@Value
@Accessors(fluent = true)
public class BusinessErrorDecision {
    String businessCode;
    String businessMessage;
    String reasonCode;
    BusinessDisposition businessDisposition;
    PhysicalOutcome physicalOutcome;
    String matchedRuleId;
    String mappingProfileId;
    String handlingAdvice;
}
