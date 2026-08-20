package com.kunling.scheduling.action.execution.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.time.Instant;

/** 首次落库的不可变执行快照，必须在网络下发之前创建。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class NewActionExecution {
    String actionInstanceId;
    String robotId;
    String deviceCommandId;
    String actionKey;
    long actionRevision;
    String downstreamActionType;
    String parameterSetId;
    Long parameterSetRevision;
    String protocolActionVersion;
    String requestHash;
    String packageHash;
    String workflowInstanceId;
    String workflowNodeInstanceId;
    JsonNode definitionSnapshot;
    JsonNode parameterSnapshot;
    JsonNode inputSnapshot;
    JsonNode commandInput;
    int timeoutMs;
    Instant createdAt;

    @ConstructorProperties({"actionInstanceId", "robotId", "deviceCommandId", "actionKey",
            "actionRevision", "downstreamActionType", "parameterSetId", "parameterSetRevision",
            "protocolActionVersion", "requestHash", "packageHash", "workflowInstanceId",
            "workflowNodeInstanceId", "definitionSnapshot", "parameterSnapshot", "inputSnapshot",
            "commandInput", "timeoutMs", "createdAt"})
    public NewActionExecution(String actionInstanceId,
                              String robotId,
                              String deviceCommandId,
                              String actionKey,
                              long actionRevision,
                              String downstreamActionType,
                              String parameterSetId,
                              Long parameterSetRevision,
                              String protocolActionVersion,
                              String requestHash,
                              String packageHash,
                              String workflowInstanceId,
                              String workflowNodeInstanceId,
                              JsonNode definitionSnapshot,
                              JsonNode parameterSnapshot,
                              JsonNode inputSnapshot,
                              JsonNode commandInput,
                              int timeoutMs,
                              Instant createdAt) {
        this.actionInstanceId = actionInstanceId;
        this.robotId = robotId;
        this.deviceCommandId = deviceCommandId;
        this.actionKey = actionKey;
        this.actionRevision = actionRevision;
        this.downstreamActionType = downstreamActionType;
        this.parameterSetId = parameterSetId;
        this.parameterSetRevision = parameterSetRevision;
        this.protocolActionVersion = protocolActionVersion;
        this.requestHash = requestHash;
        this.packageHash = packageHash;
        this.workflowInstanceId = workflowInstanceId;
        this.workflowNodeInstanceId = workflowNodeInstanceId;
        this.definitionSnapshot = definitionSnapshot;
        this.parameterSnapshot = parameterSnapshot;
        this.inputSnapshot = inputSnapshot;
        this.commandInput = commandInput;
        this.timeoutMs = timeoutMs;
        this.createdAt = createdAt;
    }
}
