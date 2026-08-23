package com.kunling.scheduling.action.exceptionmapping.domain;

/** 异常映射规则生命周期；只有 ACTIVE 规则会进入新的执行快照。 */
public enum ErrorMappingRuleStatus {
    DRAFT,
    ACTIVE,
    DISABLED
}
