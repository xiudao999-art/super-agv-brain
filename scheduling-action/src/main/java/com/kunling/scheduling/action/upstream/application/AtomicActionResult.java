package com.kunling.scheduling.action.upstream.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.execution.domain.ExecutionError;

public record AtomicActionResult(
        AtomicActionOutcome outcome,
        JsonNode output,
        JsonNode evidence,
        ExecutionError error) {
}
