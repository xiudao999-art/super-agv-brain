package com.kunling.scheduling.action.fixed.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;

/** 一期固定动作入口。调用方不能直接提交 phases 或任意脚本。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class StartFixedActionExecutionRequest {
    String actionInstanceId;
    String robotId;
    String actionType;
    JsonNode input;
    String workflowInstanceId;
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
