package com.kunling.scheduling.action.execution.domain;

public record ExecutionError(
        String code,
        String message,
        boolean physicalResultKnown,
        boolean retryable,
        String deviceCode,
        String handlingAdvice) {
}
