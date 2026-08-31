package com.kunling.scheduling.action.exceptionmapping.domain;

/** Action 交给流程引擎的安全处理上限，不代表流程跳转指令。 */
public enum HandlingConstraint {
    RETRYABLE,
    MANUAL_INTERVENTION,
    NON_RETRYABLE,
    CRITICAL
}
