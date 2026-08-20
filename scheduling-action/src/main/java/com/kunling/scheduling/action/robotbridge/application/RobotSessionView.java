package com.kunling.scheduling.action.robotbridge.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import java.time.Instant;
import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Schema(description = "当前在线的机器人连接会话")
public class RobotSessionView {
    @Schema(description = "本次 TCP 连接的会话标识")
    String sessionId;
    @Schema(description = "机器人唯一标识")
    String robotId;
    @Schema(description = "机器人类型")
    String robotType;
    @Schema(description = "机器人客户端进程实例标识")
    String clientInstanceId;
    @Schema(description = "当前机器人会话声明支持的下游主动作类型")
    Set<String> acceptedActionTypes;
    @Schema(description = "机器人连接并完成注册的时间")
    Instant connectedAt;
    @Schema(description = "最近一次收到机器人消息的时间")
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
