package com.kunling.scheduling.workflow.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class FlowTemplateCreateRequest {
    @Schema(description = "流程编号，由后端自动生成", accessMode = Schema.AccessMode.READ_ONLY)
    private String templateNumber;

    @Schema(description = "流程名称", required = true)
    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    @Schema(description = "启用状态：0-停用，1-启用；不传默认启用")
    private Integer status;

    @Schema(description = "版本号由后端固定为1", accessMode = Schema.AccessMode.READ_ONLY)
    private Integer version;

    @Schema(description = "适用对象")
    private String applicableScope;

    @Schema(description = "引用的流程模板ID", required = true)
    @NotNull(message = "请选择流程模板")
    private Long sourceTemplateId;

    @Schema(description = "流程说明")
    private String description;
}
