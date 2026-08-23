package com.kunling.scheduling.action.exceptionmapping.domain;

/** 动作对现场造成的物理结果，用于约束是否允许再次执行。 */
public enum PhysicalOutcome {
    CONFIRMED_SUCCEEDED,
    CONFIRMED_FAILED,
    PARTIALLY_COMPLETED,
    UNKNOWN
}
