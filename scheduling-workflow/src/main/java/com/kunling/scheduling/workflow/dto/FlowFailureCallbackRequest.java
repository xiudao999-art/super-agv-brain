package com.kunling.scheduling.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@Schema(description = "AGV动作失败回调")
public class FlowFailureCallbackRequest {
    @NotBlank
    private String flowId;
    @NotBlank
    private String businessKey;
    private String errorCode;
    @NotBlank
    private String errorMessage;
}
