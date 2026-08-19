package com.kunling.scheduling.action.compilation.domain;

import com.kunling.scheduling.action.definition.domain.OrderByDefinition;

public record LoopFrame(
        String stepId,
        String itemsExpression,
        String itemToken,
        int iterationIndex,
        int maxIterations,
        OrderByDefinition orderBy) {
}
