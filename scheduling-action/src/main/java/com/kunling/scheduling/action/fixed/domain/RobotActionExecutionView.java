package com.kunling.scheduling.action.fixed.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Schema(description = "一期固定动作执行结果")
public class RobotActionExecutionView {
    @Schema(description = "动作执行实例唯一标识")
    String actionInstanceId;
    @Schema(description = "目标机器人唯一标识")
    String robotId;
    @Schema(description = "发送给机器人设备层的命令标识")
    String deviceCommandId;
    @Schema(description = "固定动作类型")
    String actionType;
    @Schema(description = "固定动作业务版本")
    String actionVersion;
    @Schema(description = "下游固定 JSON 模板版本")
    String templateVersion;
    @Schema(description = "请求内容哈希，用于识别同一实例的冲突请求")
    String requestHash;
    @Schema(description = "完整动作包内容哈希")
    String packageHash;
    @Schema(description = "动作执行状态")
    RobotActionExecutionState state;
    @Schema(description = "是否已经确认物理执行结果")
    boolean physicalResultKnown;
    @Schema(description = "工作流实例标识")
    String workflowInstanceId;
    @Schema(description = "工作流节点实例标识")
    String workflowNodeInstanceId;
    @Schema(description = "调用方提交的业务参数")
    JsonNode commandInput;
    @Schema(description = "根据固定模板解析后的完整动作步骤")
    JsonNode resolvedSteps;
    @Schema(description = "机器人返回的物理执行结果")
    JsonNode physicalResult;
    @Schema(description = "结构化错误信息")
    JsonNode error;
    @Schema(description = "执行实例创建时间")
    Instant createdAt;
    @Schema(description = "执行实例最近更新时间")
    Instant updatedAt;
    @Schema(description = "执行完成时间；未结束时为空")
    Instant completedAt;
    @ConstructorProperties({"actionInstanceId", "robotId", "deviceCommandId", "actionType", "actionVersion", "templateVersion", "requestHash", "packageHash", "state", "physicalResultKnown", "workflowInstanceId", "workflowNodeInstanceId", "commandInput", "resolvedSteps", "physicalResult", "error", "createdAt", "updatedAt", "completedAt"})
    public RobotActionExecutionView(
            String actionInstanceId,
            String robotId,
            String deviceCommandId,
            String actionType,
            String actionVersion,
            String templateVersion,
            String requestHash,
            String packageHash,
            RobotActionExecutionState state,
            boolean physicalResultKnown,
            String workflowInstanceId,
            String workflowNodeInstanceId,
            JsonNode commandInput,
            JsonNode resolvedSteps,
            JsonNode physicalResult,
            JsonNode error,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt
    ) {
        this.actionInstanceId = actionInstanceId;
        this.robotId = robotId;
        this.deviceCommandId = deviceCommandId;
        this.actionType = actionType;
        this.actionVersion = actionVersion;
        this.templateVersion = templateVersion;
        this.requestHash = requestHash;
        this.packageHash = packageHash;
        this.state = state;
        this.physicalResultKnown = physicalResultKnown;
        this.workflowInstanceId = workflowInstanceId;
        this.workflowNodeInstanceId = workflowNodeInstanceId;
        this.commandInput = commandInput;
        this.resolvedSteps = resolvedSteps;
        this.physicalResult = physicalResult;
        this.error = error;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
    }

}
