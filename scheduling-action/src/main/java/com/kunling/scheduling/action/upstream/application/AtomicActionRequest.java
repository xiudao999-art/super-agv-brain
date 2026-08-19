package com.kunling.scheduling.action.upstream.application;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 下游编排器对上游的稳定调用模型。具体 HTTP/RPC 字段只允许出现在基础设施 Adapter 中。
 */
public record AtomicActionRequest(
        String robotId,
        String consumeId,
        String workflowInstanceId,
        String nodeInstanceId,
        String capabilityKey,
        JsonNode input,
        int timeoutMs) {
}
