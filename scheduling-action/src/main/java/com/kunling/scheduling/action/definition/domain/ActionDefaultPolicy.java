package com.kunling.scheduling.action.definition.domain;

public record ActionDefaultPolicy(int timeoutMs, FailurePolicy onFailure) {

    public ActionDefaultPolicy {
        timeoutMs = timeoutMs <= 0 ? 60_000 : timeoutMs;
        onFailure = onFailure == null ? FailurePolicy.abort() : onFailure;
    }
}
