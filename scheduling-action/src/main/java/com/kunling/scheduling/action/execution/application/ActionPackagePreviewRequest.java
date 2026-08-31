package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** 下发前只读预览的最小请求。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionPackagePreviewRequest {
    String actionDefinitionId;
    String robotId;

    @ConstructorProperties({"actionDefinitionId", "robotId"})
    public ActionPackagePreviewRequest(String actionDefinitionId, String robotId) {
        this.actionDefinitionId = normalize(actionDefinitionId);
        this.robotId = normalize(robotId);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
