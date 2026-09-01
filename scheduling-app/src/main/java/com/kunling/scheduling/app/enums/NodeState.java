package com.kunling.scheduling.app.enums;

public enum NodeState {
    PENDING("待执行", "节点已创建，等待进入可执行状态"),
    WAITING("等待中", "节点正在等待前置条件、设备响应或外部事件"),
    RUNNING("执行中", "节点动作已经开始执行"),
    HANG("挂起", "节点执行被挂起，等待外部接口跳过或恢复"),
    SUCCEEDED("执行成功", "节点已完成且执行结果成功"),
    FAILED("执行失败", "节点执行失败，需要按失败策略处理"),
    SKIPPED("已跳过", "节点未执行，但流程允许跳过并继续推进"),
    CANCELLED("已取消", "节点在执行前或执行过程中被取消");

    private final String label;
    private final String description;

    NodeState(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSuccessfulTerminal() {
        return this == SUCCEEDED || this == SKIPPED;
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == SKIPPED || this == CANCELLED;
    }
}
