package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionDefaultPolicy {
    int timeoutMs;
    FailurePolicy onFailure;
    @ConstructorProperties({"timeoutMs", "onFailure"})
    public ActionDefaultPolicy(
            int timeoutMs,
            FailurePolicy onFailure
    ) {
        timeoutMs = timeoutMs <= 0 ? 60_000 : timeoutMs;
        onFailure = onFailure == null ? FailurePolicy.abort() : onFailure;
        this.timeoutMs = timeoutMs;
        this.onFailure = onFailure;
    }
}
