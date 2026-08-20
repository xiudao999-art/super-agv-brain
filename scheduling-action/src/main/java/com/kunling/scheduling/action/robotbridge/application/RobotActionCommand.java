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
    @Schema(description = "目标机器人标识")
    String robotId;
    @Schema(description = "上游动作执行实例标识")
    String actionInstanceId;
    @Schema(description = "下游设备命令标识")
    String deviceCommandId;
    @Schema(description = "状态机流程实例标识")
    String workflowInstanceId;
    @Schema(description = "状态机流程节点实例标识")
    String nodeInstanceId;
    /** cnet8 线协议兼容号，序列化字段仍名为 actionVersion，不属于业务 Action 版本。 */
    @Schema(description = "cnet8 线协议兼容号，不是业务 Action 版本")
    String protocolActionVersion;
    @Schema(description = "完整动作包哈希")
    String packageHash;
    @Schema(description = "下游 MainAction 完整内容")
    JsonNode input;
    @Schema(description = "整个 Action 超时时间，单位毫秒")
    int timeoutMs;
    @Schema(description = "命令生成时间")
    Instant timestamp;
    @Schema(description = "Action 唯一标识")
    String actionKey;
    @Schema(description = "执行时冻结的 Action revision")
    long actionRevision;
    @Schema(description = "联调参数集标识")
    String parameterSetId;
    @Schema(description = "执行时冻结的联调参数集 revision")
    Long parameterSetRevision;

    @ConstructorProperties({"robotId", "actionInstanceId", "deviceCommandId", "workflowInstanceId",
            "nodeInstanceId", "protocolActionVersion", "packageHash", "input", "timeoutMs", "timestamp",
            "actionKey", "actionRevision", "parameterSetId", "parameterSetRevision"})
    public RobotActionCommand(String robotId,
                              String actionInstanceId,
                              String deviceCommandId,
                              String workflowInstanceId,
                              String nodeInstanceId,
                              String protocolActionVersion,
                              String packageHash,
                              JsonNode input,
                              int timeoutMs,
                              Instant timestamp,
                              String actionKey,
                              long actionRevision,
                              String parameterSetId,
                              Long parameterSetRevision) {
        this.robotId = robotId;
        this.actionInstanceId = actionInstanceId;
        this.deviceCommandId = deviceCommandId;
        this.workflowInstanceId = workflowInstanceId;
        this.nodeInstanceId = nodeInstanceId;
        this.protocolActionVersion = protocolActionVersion;
        this.packageHash = packageHash;
        this.input = input;
        this.timeoutMs = timeoutMs;
        this.timestamp = timestamp;
        this.actionKey = actionKey;
        this.actionRevision = actionRevision;
        this.parameterSetId = parameterSetId;
        this.parameterSetRevision = parameterSetRevision;
    }
}
