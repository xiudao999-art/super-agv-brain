package com.kunling.scheduling.agvflow.enums;

import lombok.Getter;

/** 流程实例整体运行状态。 */
@Getter
public enum FlowState {
    PENDING("待执行", "流程实例已创建，正在等待调度或等待首个节点开始执行"),
    RUNNING("执行中", "流程实例已经开始，当前正在执行或等待某个流程节点"),
    SUCCEEDED("执行成功", "流程已执行到结束节点，且整体执行结果成功"),
    FAILED("执行失败", "流程因节点失败或业务异常而终止，需要人工处理或按策略重试"),
    CANCELLED("已取消", "流程在完成前被业务操作、系统指令或异常处理主动取消");

    private final String label;
    private final String description;

    FlowState(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
