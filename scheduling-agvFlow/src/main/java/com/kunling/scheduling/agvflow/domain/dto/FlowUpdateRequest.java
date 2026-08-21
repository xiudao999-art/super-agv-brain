package com.kunling.scheduling.agvflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import com.kunling.scheduling.agvflow.enums.NodeState;

@Data
@Schema(description = "流程更新请求")
public class FlowUpdateRequest {

    @Schema(description = "流程名称", required = true, example = "A区入库流程")
    @NotBlank(message = "流程名称不能为空")
    private String flowName;

    @Schema(description = "业务订单号", required = true)
    @NotBlank(message = "订单号不能为空")
    private String orderNumber;

    @Schema(description = "关联流程模板ID", required = true, example = "1")
    @NotNull(message = "关联模板ID不能为空")
    private Long templateId;

    @Schema(description = "当前执行节点ID")
    private Long currentNodeId;

    @Schema(description = "当前节点状态")
    private NodeState currentNodeState;
}
