package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 下发前完整动作包的最小只读预览。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionPackagePreview {
    String actionDefinitionId;
    String packageHash;
    int timeoutMs;
    JsonNode commandInput;

    @ConstructorProperties({"actionDefinitionId", "packageHash", "timeoutMs", "commandInput"})
    public ActionPackagePreview(String actionDefinitionId, String packageHash,
                                int timeoutMs, JsonNode commandInput) {
        this.actionDefinitionId = actionDefinitionId;
        this.packageHash = packageHash;
        this.timeoutMs = timeoutMs;
        this.commandInput = commandInput == null ? null : commandInput.deepCopy();
    }
}
