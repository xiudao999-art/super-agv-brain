package com.kunling.scheduling.app.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exception_handling_rule")
@Schema(description = "异常处置规程主表")
public class ExceptionHandlingRule extends AppBaseEntity {
    @Schema(description = "规程编号", example = "ES-01")
    private String ruleCode;
    @Schema(description = "规程名称", example = "机械臂与自动门/舱门干涉卡阻")
    private String ruleName;
    @Schema(description = "急停范围", example = "全线急停")
    private String emergencyScope;
    @Schema(description = "处置责任", example = "现场人工 + 调度台")
    private String responsibility;
    @Schema(description = "是否只读规程")
    private Boolean readOnlyRule;
    @Schema(description = "检测信号")
    private String detectionSignal;
    @Schema(description = "当前关联工单")
    private String relatedWorkOrder;
    @Schema(description = "异常编码")
    private String exceptionCode;
    @Schema(description = "人工处置步骤JSON数组")
    private String manualSteps;
    @Schema(description = "系统自动执行说明", example = "急停触发后立即完成")
    private String automaticExecutionNote;
    @Schema(description = "恢复放行条件说明", example = "归位后由系统复核")
    private String releaseConditionNote;
    @Schema(description = "恢复放行警告")
    private String releaseWarning;
    @Schema(description = "放行权限", example = "现场人工 + 调度台")
    private String releasePermission;
    @Schema(description = "规程状态")
    private ExceptionRuleStatus status;
    @Schema(description = "备注")
    private String remark;
}
