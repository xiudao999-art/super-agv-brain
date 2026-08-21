package com.kunling.scheduling.agvflow.enums;

/** 流程实例整体运行状态。 */
public enum FlowState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
