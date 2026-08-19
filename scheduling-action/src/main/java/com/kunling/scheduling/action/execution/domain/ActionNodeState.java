package com.kunling.scheduling.action.execution.domain;

public enum ActionNodeState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    HOLDING,
    CANCELLED
}
