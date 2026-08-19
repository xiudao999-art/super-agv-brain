package com.kunling.scheduling.action.robotbridge.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

/** 对已下发动作的状态核对请求，不会触发动作重放。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RobotActionQuery {
    String robotId;
    String actionInstanceId;
    String deviceCommandId;
    @ConstructorProperties({"robotId", "actionInstanceId", "deviceCommandId"})
    public RobotActionQuery(
            String robotId,
            String actionInstanceId,
            String deviceCommandId
    ) {
        this.robotId = robotId;
        this.actionInstanceId = actionInstanceId;
        this.deviceCommandId = deviceCommandId;
    }

}
