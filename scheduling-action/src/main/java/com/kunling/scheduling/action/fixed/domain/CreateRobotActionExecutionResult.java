package com.kunling.scheduling.action.fixed.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class CreateRobotActionExecutionResult {
    boolean created;
    RobotActionExecutionView execution;
    @ConstructorProperties({"created", "execution"})
    public CreateRobotActionExecutionResult(
            boolean created,
            RobotActionExecutionView execution
    ) {
        this.created = created;
        this.execution = execution;
    }

}
