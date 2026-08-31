package com.kunling.scheduling.action.definition.domain;

/** 下游 2.0 执行器能够机械执行的有限失败指令。 */
public enum ActionFailureDirectiveType {
    RETRY_STEP,
    VERIFY_THEN_RETRY,
    SKIP_STEP,
    STOP_AND_REPORT
}
