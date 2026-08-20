package com.kunling.scheduling.action.execution.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class CreateActionExecutionResult {
    boolean created;
    ActionExecutionView execution;

    @ConstructorProperties({"created", "execution"})
    public CreateActionExecutionResult(boolean created, ActionExecutionView execution) {
        this.created = created;
        this.execution = execution;
    }
}
