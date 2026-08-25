package com.kunling.scheduling.workflow.order.domain;

import lombok.Getter;

/** 客户订单整体执行状态。 */
@Getter
public enum OrderStatus {
    QUEUED("排队中", "订单已同步入库，正在等待首个任务开始执行"),
    RUNNING("执行中", "订单至少有一个任务正在执行或等待外部处理"),
    SUCCEEDED("执行成功", "订单下的全部任务均已成功完成"),
    FAILED("执行失败", "订单中的任务执行失败，后续任务停止并等待人工处理"),
    CANCELLED("已取消", "订单在执行完成前被上游系统、业务操作或人工处理取消");

    private final String label;
    private final String description;

    OrderStatus(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** 是否已经进入不会自动继续执行的最终状态。 */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
