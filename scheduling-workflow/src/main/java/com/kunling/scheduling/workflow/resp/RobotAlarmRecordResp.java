package com.kunling.scheduling.workflow.resp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "机器人异常记录分页展示信息")
public class RobotAlarmRecordResp {

    @Schema(description = "异常编号")
    private String alarmNo;

    @Schema(description = "异常描述")
    private String alarmDescription;


    @Schema(description = "处理规则id")
    private Long handlingRuleId;

    @Schema(description = "处理规则名称")
    private String ruleName;

    @Schema(description = "处置级别：1-自动恢复，2-远程人工，3-现场人工")
    private Integer handlingLevel;

    @Schema(description = "机器人/节点")
    private String robotNode;

    @Schema(description = "发生时间")
    private LocalDateTime occurredAt;

    @Schema(description = "状态：0-待处置，1-处置中，2-已恢复，3-处置失败，4-已关闭")
    private Integer handlingStatus;

    @Schema(description = "处置人")
    private String handlingBy;

    @Schema(description = "系统执行保护")
    private List<String> systemProtection;

    @Schema(description = "核账对照")
    private Map<String, Object> accountReconciliation;

    @Schema(description = "人工确认项")
    private List<String> manualSteps;

    @JsonIgnore
    private String manualStepsJson;

    @Schema(description = "恢复检查项")
    private List<String> releaseConditions;
}
