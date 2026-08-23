package com.kunling.scheduling.action.exceptionmapping.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.kunling.scheduling.action.definition.domain.PhaseFailureAction;
import com.kunling.scheduling.action.definition.domain.RetryExhaustedAction;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 动作包内的业务异常执行策略；状态机不解释这些字段。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class PackageErrorPolicy {
    PhaseFailureAction failureStrategy;
    int maxRetries;
    int retryDelayMs;
    String verifyCapability;
    RetryExhaustedAction onExhaust;
    BusinessDisposition onExhaustDisposition;

    @ConstructorProperties({"failureStrategy", "maxRetries", "retryDelayMs", "verifyCapability",
            "onExhaust", "onExhaustDisposition"})
    public PackageErrorPolicy(PhaseFailureAction failureStrategy,
                              int maxRetries,
                              int retryDelayMs,
                              String verifyCapability,
                              RetryExhaustedAction onExhaust,
                              BusinessDisposition onExhaustDisposition) {
        this.failureStrategy = failureStrategy == null ? PhaseFailureAction.ABORT : failureStrategy;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.verifyCapability = normalize(verifyCapability);
        this.onExhaust = onExhaust == null ? RetryExhaustedAction.HOLD : onExhaust;
        this.onExhaustDisposition = onExhaustDisposition == null
                ? BusinessDisposition.MANUAL_INTERVENTION : onExhaustDisposition;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
