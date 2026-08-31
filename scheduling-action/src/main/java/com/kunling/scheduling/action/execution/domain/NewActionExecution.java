package com.kunling.scheduling.action.execution.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.time.Instant;

/** 网络下发前创建的执行记录；commandInput 是本次实际下发内容的证据。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class NewActionExecution {
    String actionInstanceId;
    String actionDefinitionId;
    String robotId;
    String deviceCommandId;
    String protocolVersion;
    String requestHash;
    String packageHash;
    JsonNode commandInput;
    int timeoutMs;
    Instant createdAt;

    @ConstructorProperties({"actionInstanceId", "actionDefinitionId", "robotId", "deviceCommandId",
            "protocolVersion", "requestHash", "packageHash", "commandInput", "timeoutMs", "createdAt"})
    public NewActionExecution(String actionInstanceId, String actionDefinitionId,
                              String robotId, String deviceCommandId,
                              String protocolVersion, String requestHash,
                              String packageHash, JsonNode commandInput,
                              int timeoutMs, Instant createdAt) {
        this.actionInstanceId = actionInstanceId;
        this.actionDefinitionId = actionDefinitionId;
        this.robotId = robotId;
        this.deviceCommandId = deviceCommandId;
        this.protocolVersion = protocolVersion;
        this.requestHash = requestHash;
        this.packageHash = packageHash;
        this.commandInput = commandInput == null ? null : commandInput.deepCopy();
        this.timeoutMs = timeoutMs;
        this.createdAt = createdAt;
    }
}
