package com.kunling.scheduling.action.execution.domain;

/** 完整动作包的运行状态；UNKNOWN_HOLD 是需要人工确认的硬边界。 */
public enum ActionExecutionState {
    DISPATCH_PENDING,
    DISPATCHED,
    ACCEPTED,
    RUNNING,
    PHYSICAL_DONE,
    FAILED,
    UNKNOWN_HOLD,
    CANCELLED;

    public boolean terminal() {
        return this == PHYSICAL_DONE || this == FAILED || this == UNKNOWN_HOLD || this == CANCELLED;
    }
}
