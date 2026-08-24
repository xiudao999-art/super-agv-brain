package com.kunling.scheduling.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Schema(description = "AGV业务流程启动参数")
public class FlowStartRequest {

    @NotBlank
    @Schema(description = "Flowable流程定义ID", example = "agvMoveProcess:1:de9d58d6")
    private String processDefinitionId;

    @NotNull
    @Schema(description = "业务任务ID", example = "123545566")
    private Long businessKey;

    @NotNull
    @Schema(description = "业务流程模板ID", example = "1")
    private Long templateId;

    @NotNull
    @Schema(description = "任务id", example = "1")
    private Long taskId;

    @NotBlank
    @Schema(description = "Flowable流程定义ID", example = "agvMoveProcess:1:de9d58d6")
    private String executionId;


    @NotNull
    @Schema(description = "Flowable流程定义ID", example = "agvMoveProcess:1:de9d58d6")
    private Long flowId;
}
