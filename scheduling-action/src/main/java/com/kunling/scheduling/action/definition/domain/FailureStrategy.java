package com.kunling.scheduling.action.definition.domain;

public enum FailureStrategy {
    ABORT,
    RETRY,
    VERIFY_BEFORE_RETRY,
    SKIP,
    HOLD
}
