package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 执行引擎发起一次主 Action 所需的最小业务命令。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ExecuteActionCommand {
    String workflowInstanceId;
    String workflowNodeInstanceId;
    String actionInstanceId;
    String robotId;
    String actionKey;
    /** 当前阶段由执行引擎明确选择；Action 不声明设备参数时允许为空。 */
    String parameterSetId;

    @ConstructorProperties({"workflowInstanceId", "workflowNodeInstanceId", "actionInstanceId",
            "robotId", "actionKey", "parameterSetId"})
    public ExecuteActionCommand(String workflowInstanceId,
                                String workflowNodeInstanceId,
                                String actionInstanceId,
                                String robotId,
                                String actionKey,
                                String parameterSetId) {
        this.workflowInstanceId = normalize(workflowInstanceId);
        this.workflowNodeInstanceId = normalize(workflowNodeInstanceId);
        this.actionInstanceId = normalize(actionInstanceId);
        this.robotId = normalize(robotId);
        this.actionKey = normalize(actionKey);
        this.parameterSetId = normalizeToNull(parameterSetId);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
