package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class FailurePolicy {
    FailureStrategy strategy;
    int maxRetries;
    @ConstructorProperties({"strategy", "maxRetries"})
    public FailurePolicy(
            FailureStrategy strategy,
            int maxRetries
    ) {
        strategy = strategy == null ? FailureStrategy.ABORT : strategy;
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries 不能小于 0");
        }
        this.strategy = strategy;
        this.maxRetries = maxRetries;
    }

    public static FailurePolicy abort() {
        return new FailurePolicy(FailureStrategy.ABORT, 0);
    }
}
