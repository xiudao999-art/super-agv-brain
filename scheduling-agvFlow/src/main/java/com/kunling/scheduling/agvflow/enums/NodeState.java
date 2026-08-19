package com.kunling.scheduling.agvflow.enums;

public enum NodeState {
    PENDING,
    WAITING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    CANCELLED;

    public boolean isSuccessfulTerminal() {
        return this == SUCCEEDED || this == SKIPPED;
    }
}
