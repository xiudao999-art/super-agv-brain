package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import com.kunling.scheduling.action.execution.domain.ExecutionError;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Schema(description = "动态动作执行实例")
public class ActionExecutionView {
    @Schema(description = "动作执行实例唯一标识")
    String actionInstanceId;
    @Schema(description = "目标机器人唯一标识")
    String robotId;
    @Schema(description = "动作编码")
    String actionKey;
    @Schema(description = "动作精确版本")
    String actionVersion;
    @Schema(description = "工作流实例标识")
    String workflowInstanceId;
    @Schema(description = "工作流节点实例标识")
    String workflowNodeInstanceId;
    @Schema(description = "执行计划内容哈希")
    String planHash;
    @Schema(description = "动态动作执行状态")
    ActionExecutionState state;
    @Schema(description = "是否已经确认物理执行结果")
    boolean physicalResultKnown;
    @Schema(description = "当前执行节点标识")
    String currentNodeId;
    @Schema(description = "动作输入参数")
    JsonNode input;
    @Schema(description = "工作流执行上下文")
    JsonNode context;
    @Schema(description = "动作执行结果")
    JsonNode result;
    @Schema(description = "结构化错误信息")
    ExecutionError error;
    @Schema(description = "是否已经收到取消请求")
    boolean cancelRequested;
    @Schema(description = "解析后的节点执行明细")
    List<ActionNodeExecutionView> resolvedSteps;
    @Schema(description = "执行实例创建时间")
    Instant createdAt;
    @Schema(description = "执行实例最近更新时间")
    Instant updatedAt;
    @Schema(description = "执行完成时间；未结束时为空")
    Instant completedAt;
    @ConstructorProperties({"actionInstanceId", "robotId", "actionKey", "actionVersion", "workflowInstanceId", "workflowNodeInstanceId", "planHash", "state", "physicalResultKnown", "currentNodeId", "input", "context", "result", "error", "cancelRequested", "resolvedSteps", "createdAt", "updatedAt", "completedAt"})
    public ActionExecutionView(
            String actionInstanceId,
            String robotId,
            String actionKey,
            String actionVersion,
            String workflowInstanceId,
            String workflowNodeInstanceId,
            String planHash,
            ActionExecutionState state,
            boolean physicalResultKnown,
            String currentNodeId,
            JsonNode input,
            JsonNode context,
            JsonNode result,
            ExecutionError error,
            boolean cancelRequested,
            List<ActionNodeExecutionView> resolvedSteps,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt
    ) {
        this.actionInstanceId = actionInstanceId;
        this.robotId = robotId;
        this.actionKey = actionKey;
        this.actionVersion = actionVersion;
        this.workflowInstanceId = workflowInstanceId;
        this.workflowNodeInstanceId = workflowNodeInstanceId;
        this.planHash = planHash;
        this.state = state;
        this.physicalResultKnown = physicalResultKnown;
        this.currentNodeId = currentNodeId;
        this.input = input;
        this.context = context;
        this.result = result;
        this.error = error;
        this.cancelRequested = cancelRequested;
        this.resolvedSteps = resolvedSteps;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
    }

}
