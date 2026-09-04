package com.kunling.scheduling.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("robot_alarm_record")
@Schema(description = "机器人异常记录")
public class RobotAlarmRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "异常编号")
    private String alarmNo;

    @Schema(description = "异常详细描述")
    private String alarmDescription;

    @Schema(description = "异常分类编码")
    private String alarmCategoryCode;

    @Schema(description = "处理规则id")
    private Long handlingRuleId;

    @Schema(description = "处置级别：1-自动恢复，2-远程人工，3-现场人工")
    private Integer handlingLevel;

    @Schema(description = "流程节点ID")
    private Long nodeId;

    @Schema(description = "影响范围")
    private String impactScope;

    @Schema(description = "处置状态：0-待处置，1-处置中，2-已恢复，3-处置失败，4-已关闭")
    private Integer handlingStatus;

    @Schema(description = "当前处理人ID")
    private Long handlerId;

    @Schema(description = "开始处置时间")
    private LocalDateTime handlingStartedAt;

    @Schema(description = "恢复时间")
    private LocalDateTime recoveredAt;

    @Schema(description = "处置结果")
    private String handlingResult;

    @Schema(description = "处置失败原因")
    private String failureReason;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建人ID")
    private Long createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新人ID")
    private Long updatedBy;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @TableLogic
    @Schema(description = "逻辑删除：0-否，1-是")
    private Integer isDeleted;
}
