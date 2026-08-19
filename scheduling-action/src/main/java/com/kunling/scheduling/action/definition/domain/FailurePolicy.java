package com.kunling.scheduling.action.definition.domain;

public record FailurePolicy(FailureStrategy strategy, int maxRetries) {

    public FailurePolicy {
        strategy = strategy == null ? FailureStrategy.ABORT : strategy;
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries 不能小于 0");
        }
    }

    public static FailurePolicy abort() {
        return new FailurePolicy(FailureStrategy.ABORT, 0);
    }
}
