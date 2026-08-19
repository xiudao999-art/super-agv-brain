package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.databind.JsonNode;

public record ConditionExpression(ConditionOperator operator, JsonNode left, JsonNode right) {
}
