package com.kunling.scheduling.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class FlowTemplateUpdateRequest {
    @Schema(description = "流程名称", required = true)
    @NotBlank(message = "流程名称不能为空")
    private String flowName;

    @Schema(description = "启用状态：0停用，1启用", required = true)
    @NotNull(message = "启用状态不能为空")
    private Integer status;

    @Schema(description = "适用对象")
    private String applicableScope;

    @Schema(description = "引用的BPMN流程模板ID", required = true)
    @NotNull(message = "请选择流程模板")
    private Long sourceTemplateId;

    @Schema(description = "流程说明")
    private String description;
}
