package com.kunling.scheduling.workflow.order.client;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "从客户系统拉取到的订单数据")
public class PulledOrder {
    @Schema(description = "订单来源系统，用于区分MES、LIMS等客户系统；与上游订单号组成幂等唯一标识",
            example = "MES")
    private String source;

    @Schema(description = "客户系统中的订单编号；同一来源下必须唯一",
            example = "MES-20260825-0031")
    private String upstreamOrderNo;

    @Schema(description = "订单优先级，通常数值越小优先级越高",
            example = "1", allowableValues = {"1", "2", "3", "4"})
    private Integer priority;

    @Schema(description = "订单在客户系统中的最后更新时间，用于增量同步和判断上游数据变化",
            example = "2026-08-25T10:00:00")
    private LocalDateTime upstreamUpdatedAt;

    @Schema(description = "客户系统正式下发订单的时间",
            example = "2026-08-25T09:58:00")
    private LocalDateTime issuedAt;

    @Schema(description = "订单包含的任务列表；任务按照taskSeq从小到大依次执行")
    private List<PulledTask> tasks = new ArrayList<>();
}
