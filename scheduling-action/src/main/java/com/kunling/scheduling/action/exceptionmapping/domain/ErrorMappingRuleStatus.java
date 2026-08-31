package com.kunling.scheduling.action.exceptionmapping.domain;

/** 异常映射规则生命周期；只有 ACTIVE 规则会参与后续 Action 组包。 */
public enum ErrorMappingRuleStatus {
    DRAFT,
    ACTIVE,
    DISABLED
}
