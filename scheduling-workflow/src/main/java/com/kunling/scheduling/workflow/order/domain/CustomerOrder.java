package com.kunling.scheduling.workflow.order.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kunling.scheduling.workflow.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("customer_order")
@Schema(description = "客户订单；一个订单包含一个或多个按顺序执行的任务")
public class CustomerOrder extends BaseEntity {
    @Schema(description = "客户系统中的订单编号；与source组成幂等唯一标识",
            example = "MES-20260825-0031")
    private String upstreamOrderNo;

    @Schema(description = "本系统自动生成的订单编号",
            example = "SYS-ORD-20260825100000-A1B2C3")
    private String systemOrderNo;

    @Schema(description = "订单来源系统，例如MES、LIMS",
            example = "MES")
    private String source;

    @Schema(description = "订单状态：QUEUED-排队中，RUNNING-执行中，SUCCEEDED-成功，FAILED-失败，CANCELLED-已取消",
            example = "RUNNING")
    private OrderStatus status;

    @Schema(description = "订单优先级：1最高，4最低",
            example = "1", allowableValues = {"1", "2", "3", "4"})
    private Integer priority;

    @Schema(description = "订单在客户系统中的最后更新时间，用于增量同步",
            example = "2026-08-25T10:00:00")
    private LocalDateTime upstreamUpdatedAt;

    @Schema(description = "客户系统正式下发订单的时间",
            example = "2026-08-25T09:58:00")
    private LocalDateTime issuedAt;

    @Schema(description = "订单最近一次执行或调度错误编码",
            example = "TASK_EXECUTION_FAILED")
    private String errorCode;

    @Schema(description = "订单最近一次执行或调度错误信息",
            example = "未找到在线机器人")
    private String errorMessage;

}
