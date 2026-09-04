package com.kunling.scheduling.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class RobotInfoSearchDto {
    @Schema(description = "订单编号")
    private String orderNo;

    @Schema(description = "机器人编码")
    private String robotCode;

    @Schema(description = "机器人名称")
    private String robotName;
}
