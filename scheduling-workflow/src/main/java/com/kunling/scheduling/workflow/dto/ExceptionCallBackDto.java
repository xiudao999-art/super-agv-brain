package com.kunling.scheduling.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ExceptionCallBackDto {

    @Schema(description = "处理方式")
    private String dealStatus;

    @Schema(description = "异常id")
    private Long alarmId;


    private Long nodeId;
}
