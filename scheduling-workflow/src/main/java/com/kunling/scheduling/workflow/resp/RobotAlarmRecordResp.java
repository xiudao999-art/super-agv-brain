package com.kunling.scheduling.workflow.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "机器人异常记录分页展示信息")
public class RobotAlarmRecordResp {

    @Schema(description = "异常编号")
    private String alarmNo;

    @Schema(description = "异常描述")
    private String alarmDescription;

    @Schema(description = "处置级别：1-自动恢复，2-远程人工，3-现场人工")
    private Integer handlingLevel;

    @Schema(description = "机器人/节点")
    private String robotNode;

    @Schema(description = "发生时间")
    private LocalDateTime occurredAt;

    @Schema(description = "状态：0-待处置，1-处置中，2-已恢复，3-处置失败，4-已关闭")
    private Integer handlingStatus;
}
