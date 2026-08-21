package com.kunling.scheduling.action.execution.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.time.Instant;

/** 动作执行记录；所有快照字段都反映开始执行时的实际内容。 */
@Schema(description = "完整动作包执行记录")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionExecutionView {
    @Schema(description = "动作执行实例标识")
    String actionInstanceId;
    @Schema(description = "机器人标识")
    String robotId;
    @Schema(description = "下游设备命令标识")
    String deviceCommandId;
    @Schema(description = "Action 唯一标识")
    String actionKey;
    @Schema(description = "执行时冻结的 Action revision")
    long actionRevision;
    @Schema(description = "下游主动作类型")
    String downstreamActionType;
    @Schema(description = "联调参数集标识")
    String parameterSetId;
    @Schema(description = "执行时冻结的联调参数集 revision")
    Long parameterSetRevision;
    @Schema(description = "cnet8 线协议兼容号，不是业务 Action 版本")
    String protocolActionVersion;
    @Schema(description = "幂等请求哈希")
    String requestHash;
    @Schema(description = "最终动作包哈希")
    String packageHash;
    @Schema(description = "上游执行状态")
    ActionExecutionState state;
    @Schema(description = "物理执行结果是否已经确定")
    boolean physicalResultKnown;
    @Schema(description = "状态机流程实例标识")
    String workflowInstanceId;
    @Schema(description = "状态机流程节点实例标识")
    String workflowNodeInstanceId;
    @Schema(description = "Action 定义执行快照")
    JsonNode definitionSnapshot;
    @Schema(description = "联调参数执行快照")
    JsonNode parameterSnapshot;
    @Schema(description = "下发给 cnet8 的 MainAction 完整内容")
    JsonNode commandInput;
    @Schema(description = "逐阶段最终参数与执行证据")
    JsonNode resolvedSteps;
    @Schema(description = "下游确认的物理执行结果")
    JsonNode physicalResult;
    @Schema(description = "失败或不确定状态的原始异常信息")
    JsonNode error;
    @Schema(description = "创建时间")
    Instant createdAt;
    @Schema(description = "最后更新时间")
    Instant updatedAt;
    @Schema(description = "确定终态完成时间；未完成时为空")
    Instant completedAt;

    @ConstructorProperties({"actionInstanceId", "robotId", "deviceCommandId", "actionKey",
            "actionRevision", "downstreamActionType", "parameterSetId", "parameterSetRevision",
            "protocolActionVersion", "requestHash", "packageHash", "state", "physicalResultKnown",
            "workflowInstanceId", "workflowNodeInstanceId", "definitionSnapshot", "parameterSnapshot",
            "commandInput", "resolvedSteps", "physicalResult", "error",
            "createdAt", "updatedAt", "completedAt"})
    public ActionExecutionView(String actionInstanceId,
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
                               ActionExecutionState state,
                               boolean physicalResultKnown,
                               String workflowInstanceId,
                               String workflowNodeInstanceId,
                               JsonNode definitionSnapshot,
                               JsonNode parameterSnapshot,
                               JsonNode commandInput,
                               JsonNode resolvedSteps,
                               JsonNode physicalResult,
                               JsonNode error,
                               Instant createdAt,
                               Instant updatedAt,
                               Instant completedAt) {
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
        this.state = state;
        this.physicalResultKnown = physicalResultKnown;
        this.workflowInstanceId = workflowInstanceId;
        this.workflowNodeInstanceId = workflowNodeInstanceId;
        this.definitionSnapshot = definitionSnapshot;
        this.parameterSnapshot = parameterSnapshot;
        this.commandInput = commandInput;
        this.resolvedSteps = resolvedSteps;
        this.physicalResult = physicalResult;
        this.error = error;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
    }
}
