package com.kunling.scheduling.action.fixed.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/** 一期固定动作入口。调用方不能直接提交 phases 或任意脚本。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Schema(description = "一期固定动作执行请求；调用方只能提交白名单业务参数，不能直接提交动作步骤")
public class StartFixedActionExecutionRequest {
    @Schema(description = "动作执行实例唯一标识，由调度系统生成", example = "action-20260819-001", required = true)
    String actionInstanceId;
    @Schema(description = "目标机器人唯一标识", example = "R01", required = true)
    String robotId;
    @Schema(description = "固定动作类型", allowableValues = {"MOVE", "ARM.HOME", "ARM.PICK", "ARM.PLACE"}, required = true)
    String actionType;
    @Schema(description = "固定动作的业务输入参数，具体字段由所选动作模板约束", required = true)
    JsonNode input;
    @Schema(description = "工作流实例标识，用于执行链路追踪", example = "workflow-001")
    String workflowInstanceId;
    @Schema(description = "工作流节点实例标识，用于执行链路追踪", example = "node-001")
    String workflowNodeInstanceId;
    @ConstructorProperties({"actionInstanceId", "robotId", "actionType", "input", "workflowInstanceId", "workflowNodeInstanceId"})
    public StartFixedActionExecutionRequest(
            String actionInstanceId,
            String robotId,
            String actionType,
            JsonNode input,
            String workflowInstanceId,
            String workflowNodeInstanceId
    ) {
        this.actionInstanceId = actionInstanceId;
        this.robotId = robotId;
        this.actionType = actionType;
        this.input = input;
        this.workflowInstanceId = workflowInstanceId;
        this.workflowNodeInstanceId = workflowNodeInstanceId;
    }

}
