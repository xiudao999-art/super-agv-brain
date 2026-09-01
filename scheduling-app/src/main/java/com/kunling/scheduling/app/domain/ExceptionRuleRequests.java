package com.kunling.scheduling.app.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public final class ExceptionRuleRequests {
    private ExceptionRuleRequests() { }

    @Data
    public static class Save {
        @NotBlank private String ruleCode;
        @NotBlank private String ruleName;
        @NotBlank private String emergencyScope;
        @NotBlank private String responsibility;
        private Boolean readOnlyRule = false;
        @NotBlank private String detectionSignal;
        private String relatedWorkOrder;
        @NotBlank private String exceptionCode;
        @Schema(description = "人工处置步骤，按数组顺序执行")
        private List<String> manualSteps = new ArrayList<>();
        private String automaticExecutionNote;
        private String releaseConditionNote;
        private String releaseWarning;
        private String releasePermission;
        private String remark;
        @Schema(description = "系统自动执行步骤")
        private List<String> systemActions = new ArrayList<>();
        @Schema(description = "恢复放行条件")
        private List<String> releaseConditions = new ArrayList<>();
    }

    @Data
    public static class ChangeStatus {
        @NotNull private ExceptionRuleStatus status;
    }
}
