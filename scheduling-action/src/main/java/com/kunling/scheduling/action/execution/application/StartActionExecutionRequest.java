package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Schema(description = "动态动作执行请求；当前一期默认关闭")
public class StartActionExecutionRequest {
    @Schema(description = "动作执行实例唯一标识", required = true)
    String actionInstanceId;
    @Schema(description = "目标机器人唯一标识", required = true)
    String robotId;
    @Schema(description = "已发布动作编码", required = true)
    String actionKey;
    @Schema(description = "已发布动作精确版本", example = "1.0.0", required = true)
    String actionVersion;
    @Schema(description = "工作流实例标识")
    String workflowInstanceId;
    @Schema(description = "工作流节点实例标识")
    String workflowNodeInstanceId;
    @Schema(description = "符合动作输入 Schema 的业务参数", required = true)
    JsonNode input;
    @Schema(description = "由工作流传入的只读执行上下文")
    JsonNode context;
    @ConstructorProperties({"actionInstanceId", "robotId", "actionKey", "actionVersion", "workflowInstanceId", "workflowNodeInstanceId", "input", "context"})
    public StartActionExecutionRequest(
            String actionInstanceId,
            String robotId,
            String actionKey,
            String actionVersion,
            String workflowInstanceId,
            String workflowNodeInstanceId,
            JsonNode input,
            JsonNode context
    ) {
        this.actionInstanceId = actionInstanceId;
        this.robotId = robotId;
        this.actionKey = actionKey;
        this.actionVersion = actionVersion;
        this.workflowInstanceId = workflowInstanceId;
        this.workflowNodeInstanceId = workflowNodeInstanceId;
        this.input = input;
        this.context = context;
    }

}
