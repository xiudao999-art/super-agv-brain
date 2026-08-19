package com.kunling.scheduling.action.execution.domain;

public enum ActionExecutionState {
    ACCEPTED,
    RUNNING,
    PHYSICAL_DONE,
    FAILED,
    UNKNOWN_HOLD,
    CANCELLED;

    public boolean isTerminal() {
        return this == PHYSICAL_DONE || this == FAILED || this == UNKNOWN_HOLD || this == CANCELLED;
    }
}
