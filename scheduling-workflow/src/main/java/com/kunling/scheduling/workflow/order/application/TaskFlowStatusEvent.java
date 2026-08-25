package com.kunling.scheduling.workflow.order.application;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TaskFlowStatusEvent {
    public enum Type { SUCCEEDED, WAITING, FAILED }
    private final Long taskId;
    private final Type type;
    private final String processInstanceId;
    private final String currentStep;
    private final String message;
}
