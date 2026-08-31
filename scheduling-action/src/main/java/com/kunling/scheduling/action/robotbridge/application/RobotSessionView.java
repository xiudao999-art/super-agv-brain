package com.kunling.scheduling.action.robotbridge.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Schema(description = "当前在线的机器人连接会话")
public class RobotSessionView {
    String sessionId;
    String robotId;
    String robotType;
    String clientInstanceId;
    Map<String, RobotOperationCapability> operationCapabilities;
    Set<String> policyFeatures;
    Instant connectedAt;
    Instant lastSeenAt;

    @ConstructorProperties({"sessionId", "robotId", "robotType", "clientInstanceId",
            "operationCapabilities", "policyFeatures", "connectedAt", "lastSeenAt"})
    public RobotSessionView(String sessionId,
                            String robotId,
                            String robotType,
                            String clientInstanceId,
                            Map<String, RobotOperationCapability> operationCapabilities,
                            Set<String> policyFeatures,
                            Instant connectedAt,
                            Instant lastSeenAt) {
        this.sessionId = sessionId;
        this.robotId = robotId;
        this.robotType = robotType;
        this.clientInstanceId = clientInstanceId;
        this.operationCapabilities = Collections.unmodifiableMap(
                new LinkedHashMap<String, RobotOperationCapability>(operationCapabilities));
        this.policyFeatures = Collections.unmodifiableSet(new LinkedHashSet<String>(policyFeatures));
        this.connectedAt = connectedAt;
        this.lastSeenAt = lastSeenAt;
    }
}
