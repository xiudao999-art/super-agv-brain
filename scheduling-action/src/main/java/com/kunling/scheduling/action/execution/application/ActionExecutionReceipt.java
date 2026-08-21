package com.kunling.scheduling.action.execution.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;

import java.time.Instant;

/** Action 已创建执行快照并尝试下发后的本地调用回执；不是物理执行结果。 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionExecutionReceipt {
    String actionInstanceId;
    Instant submittedAt;
}
