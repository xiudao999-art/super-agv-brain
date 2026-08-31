package com.kunling.scheduling.action.execution.domain;

/** Action 向执行引擎交付的最终结果。 */
public enum ActionExecutionResult {
    SUCCEEDED,
    FAILED,
    UNKNOWN_HOLD
}
