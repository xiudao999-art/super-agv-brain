package com.kunling.scheduling.action.execution.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ExecutionError {
    String code;
    String message;
    boolean physicalResultKnown;
    boolean retryable;
    String deviceCode;
    String handlingAdvice;
    @ConstructorProperties({"code", "message", "physicalResultKnown", "retryable", "deviceCode", "handlingAdvice"})
    public ExecutionError(
            String code,
            String message,
            boolean physicalResultKnown,
            boolean retryable,
            String deviceCode,
            String handlingAdvice
    ) {
        this.code = code;
        this.message = message;
        this.physicalResultKnown = physicalResultKnown;
        this.retryable = retryable;
        this.deviceCode = deviceCode;
        this.handlingAdvice = handlingAdvice;
    }

}
