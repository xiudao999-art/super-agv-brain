package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** Action 中一个有序原子操作步骤。 */
@Schema(description = "Action 中一个有序原子操作步骤")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionStepDefinition {
    String stepId;
    String operation;
    JsonNode params;
    boolean gate;
    ActionFailurePolicy onFailure;

    @ConstructorProperties({"stepId", "operation", "params", "gate", "onFailure"})
    public ActionStepDefinition(String stepId,
                                String operation,
                                JsonNode params,
                                boolean gate,
                                ActionFailurePolicy onFailure) {
        this.stepId = normalize(stepId);
        this.operation = normalize(operation);
        this.params = params == null ? null : params.deepCopy();
        this.gate = gate;
        this.onFailure = onFailure;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
