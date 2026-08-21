package com.kunling.scheduling.agvflow.domain.dto;

import com.kunling.scheduling.agvflow.enums.NodeState;
import com.kunling.scheduling.agvflow.enums.FlowState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Min;

@Data
@Schema(description = "订单流程当前节点进度更新请求")
public class FlowProgressUpdateRequest {

    @NotNull(message = "当前节点ID不能为空")
    @Schema(description = "当前执行的模板节点ID", required = true)
    private Long currentNodeId;

    @NotNull(message = "当前节点状态不能为空")
    @Schema(description = "当前节点状态", required = true)
    private NodeState currentNodeState;

    @Schema(description = "流程整体状态；不传时根据节点状态自动推导")
    private FlowState flowState;

    @Schema(description = "本次状态事件ID，用于幂等和问题追踪")
    private Long eventId;

    @Schema(description = "错误编码")
    private String errorCode;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "已重试次数；不传则保持原值")
    @Min(value = 0, message = "重试次数不能小于0")
    private Integer attempt;
}
