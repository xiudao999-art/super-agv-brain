package com.kunling.scheduling.action.definition.domain;

/** 下游 PhaseFailAction 的上游强类型表示。 */
public enum PhaseFailureAction {
    ABORT,
    RETRY_PHASE,
    VERIFY_BEFORE_RETRY,
    SKIP
}
