package com.kunling.scheduling.action.capability.domain;

public enum CapabilityRetrySafety {
    SAFE,
    IDEMPOTENT,
    VERIFY_BEFORE_RETRY,
    NEVER
}
