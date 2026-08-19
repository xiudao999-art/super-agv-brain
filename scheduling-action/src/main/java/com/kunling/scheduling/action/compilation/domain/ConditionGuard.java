package com.kunling.scheduling.action.compilation.domain;

import com.kunling.scheduling.action.definition.domain.ConditionExpression;

public record ConditionGuard(String stepId, ConditionExpression condition, boolean expected) {
}
