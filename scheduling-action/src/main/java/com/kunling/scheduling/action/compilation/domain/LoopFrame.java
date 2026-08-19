package com.kunling.scheduling.action.compilation.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.definition.domain.OrderByDefinition;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LoopFrame {
    String stepId;
    String itemsExpression;
    String itemToken;
    int iterationIndex;
    int maxIterations;
    OrderByDefinition orderBy;
    @ConstructorProperties({"stepId", "itemsExpression", "itemToken", "iterationIndex", "maxIterations", "orderBy"})
    public LoopFrame(
            String stepId,
            String itemsExpression,
            String itemToken,
            int iterationIndex,
            int maxIterations,
            OrderByDefinition orderBy
    ) {
        this.stepId = stepId;
        this.itemsExpression = itemsExpression;
        this.itemToken = itemToken;
        this.iterationIndex = iterationIndex;
        this.maxIterations = maxIterations;
        this.orderBy = orderBy;
    }

}
