package com.kunling.scheduling.action.fixed.domain;

/** 一期整包动作的下游状态；DISPATCHED 表示只确认字节已写出，尚未确认物理结果。 */
public enum RobotActionExecutionState {
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

    public boolean mayHavePhysicalSideEffects() {
        return this != DISPATCH_PENDING;
    }
}
