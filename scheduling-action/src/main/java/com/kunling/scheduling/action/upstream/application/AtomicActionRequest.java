package com.kunling.scheduling.action.upstream.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 下游编排器对上游的稳定调用模型。具体 HTTP/RPC 字段只允许出现在基础设施 Adapter 中。
 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class AtomicActionRequest {
    String robotId;
    String consumeId;
    String workflowInstanceId;
    String nodeInstanceId;
    String capabilityKey;
    JsonNode input;
    int timeoutMs;
    @ConstructorProperties({"robotId", "consumeId", "workflowInstanceId", "nodeInstanceId", "capabilityKey", "input", "timeoutMs"})
    public AtomicActionRequest(
            String robotId,
            String consumeId,
            String workflowInstanceId,
            String nodeInstanceId,
            String capabilityKey,
            JsonNode input,
            int timeoutMs
    ) {
        this.robotId = robotId;
        this.consumeId = consumeId;
        this.workflowInstanceId = workflowInstanceId;
        this.nodeInstanceId = nodeInstanceId;
        this.capabilityKey = capabilityKey;
        this.input = input;
        this.timeoutMs = timeoutMs;
    }

}
