package com.kunling.scheduling.action.robotbridge.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import java.time.Instant;
import java.util.Set;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RobotSessionView {
    String sessionId;
    String robotId;
    String robotType;
    String clientInstanceId;
    Set<String> acceptedActionTypes;
    Instant connectedAt;
    Instant lastSeenAt;
    @ConstructorProperties({"sessionId", "robotId", "robotType", "clientInstanceId", "acceptedActionTypes", "connectedAt", "lastSeenAt"})
    public RobotSessionView(
            String sessionId,
            String robotId,
            String robotType,
            String clientInstanceId,
            Set<String> acceptedActionTypes,
            Instant connectedAt,
            Instant lastSeenAt
    ) {
        this.sessionId = sessionId;
        this.robotId = robotId;
        this.robotType = robotType;
        this.clientInstanceId = clientInstanceId;
        this.acceptedActionTypes = acceptedActionTypes;
        this.connectedAt = connectedAt;
        this.lastSeenAt = lastSeenAt;
    }

}
