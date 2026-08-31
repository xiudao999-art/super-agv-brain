package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 一个步骤失败后可直接交给下游解释器执行的指令。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionFailureDirective {
    ActionFailureDirectiveType action;
    int maxRetries;
    int delayMs;
    String verifyOperation;
    JsonNode verifyParams;
    ActionFailureDirectiveType onExhaust;

    @ConstructorProperties({"action", "maxRetries", "delayMs", "verifyOperation",
            "verifyParams", "onExhaust"})
    public ActionFailureDirective(ActionFailureDirectiveType action,
                                  int maxRetries,
                                  int delayMs,
                                  String verifyOperation,
                                  @JsonProperty("verifyParams") JsonNode verifyParams,
                                  ActionFailureDirectiveType onExhaust) {
        this.action = action;
        this.maxRetries = maxRetries;
        this.delayMs = delayMs;
        this.verifyOperation = normalize(verifyOperation);
        this.verifyParams = verifyParams == null || verifyParams.isNull()
                ? null : verifyParams.deepCopy();
        this.onExhaust = onExhaust;
    }

    public static ActionFailureDirective stopAndReport() {
        return new ActionFailureDirective(ActionFailureDirectiveType.STOP_AND_REPORT,
                0, 0, null, null, null);
    }

    public static ActionFailureDirective skipStep() {
        return new ActionFailureDirective(ActionFailureDirectiveType.SKIP_STEP,
                0, 0, null, null, null);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
