package com.kunling.scheduling.action.definition.domain;

/** 重试次数耗尽后的下游处置。 */
public enum RetryExhaustedAction {
    HOLD,
    CANCEL,
    MANUAL
}
