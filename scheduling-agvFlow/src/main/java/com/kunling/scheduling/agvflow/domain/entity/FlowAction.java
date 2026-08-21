package com.kunling.scheduling.agvflow.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kunling.scheduling.agvflow.enums.FailureStrategyEnums;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName(value = "flow_action", autoResultMap = true)
public class FlowAction extends BaseEntity {

    @Schema(description = "所属节点")
    private Long machineId;

    @Schema(description = "动作名称")
    private String actionName;

    @Schema(description = "动作编码")
    private String actionCode;

    @Schema(description = "节点分类: GENERAL/MAIN/OTHER")
    private String nodeCategory;

    @Schema(description = "适用对象")
    private String applicableScope;

    @Schema(description = "失败策略")
    private FailureStrategyEnums failureStrategy;

    @Schema(description = "完成依据")
    private String completionCriteria;

    @Schema(description = "后继子节点ID；为空表示子流程结束")
    private Long nextActionId;
}
