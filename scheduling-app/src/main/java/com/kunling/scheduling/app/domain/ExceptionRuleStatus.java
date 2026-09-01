package com.kunling.scheduling.app.domain;

import lombok.Getter;

@Getter
public enum ExceptionRuleStatus {
    ENABLED("启用"), DISABLED("停用");

    private final String label;

    ExceptionRuleStatus(String label) {
        this.label = label;
    }
}
