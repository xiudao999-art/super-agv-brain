package com.kunling.scheduling.action.compilation.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.definition.domain.ConditionExpression;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ConditionGuard {
    String stepId;
    ConditionExpression condition;
    boolean expected;
    @ConstructorProperties({"stepId", "condition", "expected"})
    public ConditionGuard(
            String stepId,
            ConditionExpression condition,
            boolean expected
    ) {
        this.stepId = stepId;
        this.condition = condition;
        this.expected = expected;
    }

}
