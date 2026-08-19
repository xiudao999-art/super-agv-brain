package com.kunling.scheduling.action.upstream.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.execution.domain.ExecutionError;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class AtomicActionResult {
    AtomicActionOutcome outcome;
    JsonNode output;
    JsonNode evidence;
    ExecutionError error;
    @ConstructorProperties({"outcome", "output", "evidence", "error"})
    public AtomicActionResult(
            AtomicActionOutcome outcome,
            JsonNode output,
            JsonNode evidence,
            ExecutionError error
    ) {
        this.outcome = outcome;
        this.output = output;
        this.evidence = evidence;
        this.error = error;
    }

}
