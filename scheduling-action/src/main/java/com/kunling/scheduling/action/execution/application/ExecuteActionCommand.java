package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 执行引擎发起一次 Action 的完整公共命令。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ExecuteActionCommand {
    String actionInstanceId;
    String actionDefinitionId;
    String robotId;

    @ConstructorProperties({"actionInstanceId", "actionDefinitionId", "robotId"})
    public ExecuteActionCommand(String actionInstanceId, String actionDefinitionId, String robotId) {
        this.actionInstanceId = normalize(actionInstanceId);
        this.actionDefinitionId = normalize(actionDefinitionId);
        this.robotId = normalize(robotId);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
