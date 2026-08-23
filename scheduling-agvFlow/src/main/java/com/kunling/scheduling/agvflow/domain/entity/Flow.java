package com.kunling.scheduling.agvflow.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.kunling.scheduling.agvflow.enums.FlowState;
import com.kunling.scheduling.agvflow.enums.NodeState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@TableName("flow")
public class Flow extends BaseEntity {
    @Schema(description = "任务id")
    private Long taskId;

    @Schema(description = "流程名称")
    private String flowName;

    @Schema(description = "业务订单号")
    private String orderNumber;

    @Schema(description = "关联流程模板ID")
    private Long templateId;

    @Schema(description = "流程创建时使用的模板版本")
    private Integer templateVersion;

    @Schema(description = "流程整体状态")
    private FlowState flowState;

    @Schema(description = "当前执行的模板节点ID")
    private Long currentNodeId;

    @Schema(description = "当前节点状态")
    private NodeState currentNodeState;

    @Schema(description = "流程开始时间")
    private LocalDateTime startedAt;

    @Schema(description = "流程完成时间")
    private LocalDateTime completedAt;

    @Version
    @Schema(description = "乐观锁版本号")
    private Integer version;

    @Schema(description = "最后处理的事件ID，用于事件幂等")
    private Long lastEventId;

    @Schema(description = "最近一次错误编码")
    private String errorCode;

    @Schema(description = "最近一次错误信息")
    private String errorMessage;

    @Schema(description = "已重试次数")
    private Integer attempt;

    private String processDefinitionId;

    private Long processInstanceId;
}
