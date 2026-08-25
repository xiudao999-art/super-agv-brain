package com.kunling.scheduling.workflow.order.domain;

public enum OrderTaskStatus {
    QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED;

    public boolean executionOwned() {
        return this == QUEUED || this == RUNNING
                || this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
