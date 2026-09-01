package com.kunling.scheduling.app.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;
import java.util.List;

public final class ExceptionRuleResponses {
    private ExceptionRuleResponses() { }

    @Data
    @AllArgsConstructor
    public static class Summary {
        private Long id;
        private String ruleCode;
        private String ruleName;
        private String detectionSignal;
        private String emergencyScope;
        private String responsibility;
        private String relatedWorkOrder;
        private ExceptionRuleStatus status;
    }

    @Data
    @AllArgsConstructor
    public static class Detail {
        private Long id;
        private String ruleCode;
        private String ruleName;
        private String emergencyScope;
        private String responsibility;
        private Boolean readOnlyRule;
        private String detectionSignal;
        private String relatedWorkOrder;
        private String exceptionCode;
        private List<String> systemActions;
        private List<String> manualSteps;
        private List<String> releaseConditions;
        private String automaticExecutionNote;
        private String releaseConditionNote;
        private String releaseWarning;
        private String releasePermission;
        private ExceptionRuleStatus status;
        private String statusLabel;
        private String remark;
        private Date createTime;
        private Date updateTime;
    }
}
