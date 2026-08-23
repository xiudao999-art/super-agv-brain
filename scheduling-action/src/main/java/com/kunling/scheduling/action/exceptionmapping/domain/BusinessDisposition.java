package com.kunling.scheduling.action.exceptionmapping.domain;

/** 状态机唯一需要理解的业务异常处置分类。 */
public enum BusinessDisposition {
    RETRYABLE,
    MANUAL_INTERVENTION,
    NON_RETRYABLE,
    CRITICAL
}
