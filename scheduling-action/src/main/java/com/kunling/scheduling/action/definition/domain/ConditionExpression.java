package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ConditionExpression {
    ConditionOperator operator;
    JsonNode left;
    JsonNode right;
    @ConstructorProperties({"operator", "left", "right"})
    public ConditionExpression(
            ConditionOperator operator,
            JsonNode left,
            JsonNode right
    ) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

}
