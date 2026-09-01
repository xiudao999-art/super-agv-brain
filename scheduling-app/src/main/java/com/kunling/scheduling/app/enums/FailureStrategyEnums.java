package com.kunling.scheduling.app.enums;

import lombok.Data;
import lombok.Getter;

@Getter
public enum FailureStrategyEnums {

    SUSPEND_AFTER_RETRYING("SUSPEND_AFTER_RETRYING", "重试后挂起"),
    NOTIFY_OPERATORS("NOTIFY_OPERATORS", "立即挂起并通知操作人员"),
    //子节点
    CHILD_NODE("CHILD_NODE","子节点"),
    OTHER("OTHER", "其他节点"),
    ;

    private final String code;
    private final String label;

    FailureStrategyEnums(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
