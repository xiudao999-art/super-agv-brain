package com.kunling.scheduling.action.fixed.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;

/** 已完成参数物化、可以直接放入上游 COMMAND.input 的动作包。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class MaterializedFixedActionPackage {
    FixedActionType actionType;
    String actionVersion;
    String templateVersion;
    int timeoutMs;
    JsonNode commandInput;
    String packageHash;
    @ConstructorProperties({"actionType", "actionVersion", "templateVersion", "timeoutMs", "commandInput", "packageHash"})
    public MaterializedFixedActionPackage(
            FixedActionType actionType,
            String actionVersion,
            String templateVersion,
            int timeoutMs,
            JsonNode commandInput,
            String packageHash
    ) {
        this.actionType = actionType;
        this.actionVersion = actionVersion;
        this.templateVersion = templateVersion;
        this.timeoutMs = timeoutMs;
        this.commandInput = commandInput;
        this.packageHash = packageHash;
    }

}
