package com.kunling.scheduling.app.domain;

import lombok.Getter;

@Getter
public enum ExceptionRuleItemType {
    SYSTEM_ACTION("系统自动执行"), RELEASE_CONDITION("恢复放行条件");

    private final String label;

    ExceptionRuleItemType(String label) {
        this.label = label;
    }
}
