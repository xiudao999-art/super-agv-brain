package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 预览和正式执行共用的请求；正式执行必须回传预览得到的 packageHash。 */
@Schema(description = "完整动作包预览或执行请求")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class StartActionExecutionRequest {
    @Schema(description = "动作执行实例标识；预览时可为空，正式执行时必填")
    String actionInstanceId;
    @Schema(description = "目标机器人标识", example = "R01")
    String robotId;
    @Schema(description = "Action 唯一标识", example = "ARM.PICK")
    String actionKey;
    @Schema(description = "设备联调参数集标识；Action 无联调参数时可为空")
    String parameterSetId;
    @Schema(description = "预览返回的 packageHash；正式执行时必填")
    String expectedPackageHash;
    @Schema(description = "状态机流程实例标识")
    String workflowInstanceId;
    @Schema(description = "状态机流程节点实例标识")
    String workflowNodeInstanceId;

    @ConstructorProperties({"actionInstanceId", "robotId", "actionKey", "parameterSetId",
            "expectedPackageHash", "workflowInstanceId", "workflowNodeInstanceId"})
    public StartActionExecutionRequest(String actionInstanceId,
                                       String robotId,
                                       String actionKey,
                                       String parameterSetId,
                                       String expectedPackageHash,
                                       String workflowInstanceId,
                                       String workflowNodeInstanceId) {
        this.actionInstanceId = normalizeToNull(actionInstanceId);
        this.robotId = normalize(robotId);
        this.actionKey = normalize(actionKey);
        this.parameterSetId = normalizeToNull(parameterSetId);
        this.expectedPackageHash = normalizeToNull(expectedPackageHash);
        this.workflowInstanceId = normalizeToNull(workflowInstanceId);
        this.workflowNodeInstanceId = normalizeToNull(workflowNodeInstanceId);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
