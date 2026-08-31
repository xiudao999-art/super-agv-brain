package com.kunling.scheduling.action.robotbridge.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.time.Instant;

/** 与传输方式无关的完整动作包命令。 */
@Schema(description = "发送给机器人适配层的完整动作包命令")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RobotActionCommand {
    String robotId;
    String actionInstanceId;
    String deviceCommandId;
    String protocolVersion;
    String packageHash;
    JsonNode input;
    int timeoutMs;
    Instant timestamp;

    @ConstructorProperties({"robotId", "actionInstanceId", "deviceCommandId", "protocolVersion",
            "packageHash", "input", "timeoutMs", "timestamp"})
    public RobotActionCommand(String robotId,
                              String actionInstanceId,
                              String deviceCommandId,
                              String protocolVersion,
                              String packageHash,
                              JsonNode input,
                              int timeoutMs,
                              Instant timestamp) {
        this.robotId = robotId;
        this.actionInstanceId = actionInstanceId;
        this.deviceCommandId = deviceCommandId;
        this.protocolVersion = protocolVersion;
        this.packageHash = packageHash;
        this.input = input == null ? null : input.deepCopy();
        this.timeoutMs = timeoutMs;
        this.timestamp = timestamp;
    }
}
