package com.kunling.scheduling.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.kunling.scheduling.agvflow.enums.NodeState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("node_state_transition_rule")
@Schema(description = "节点状态流转规则")
public class NodeStateTransitionRule {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "规则集编码，用于区分不同类型节点", example = "COMMON_NODE")
    private String ruleSetCode;

    @Schema(description = "当前状态")
    private NodeState currentState;

    @Schema(description = "外部事件或系统事件")
    private String eventCode;

    @Schema(description = "流转后的状态")
    private NodeState nextState;

    @Schema(description = "流转后是否进入终态：0否，1是", example = "0")
    private Integer terminalFlag;

    @Schema(description = "终态结果：SUCCESS/FAILURE/CANCEL/SKIP")
    private String terminalResult;

    @Schema(description = "是否增加重试次数：0否，1是", example = "0")
    private Integer incrementAttempt;

    @Schema(description = "是否启用：0否，1是", example = "1")
    private Integer enabled;

    @Schema(description = "规则描述")
    private String description;

    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    @Schema(description = "更新时间", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime updatedAt;
}
