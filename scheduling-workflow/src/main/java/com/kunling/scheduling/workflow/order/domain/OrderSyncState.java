package com.kunling.scheduling.workflow.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_sync_state")
@Schema(description = "客户订单同步水位，记录每个订单来源最近一次同步执行情况")
public class OrderSyncState {
    @TableId(type = IdType.AUTO)
    @Schema(description = "同步状态记录主键ID", example = "1")
    private Long id;

    @Schema(description = "订单来源；每个来源只保存一条同步水位记录", example = "MES")
    private String source;

    @Schema(description = "最近一次完整同步成功的截止时间；下次同步以此时间减去重叠窗口作为查询起点",
            example = "2026-08-25T10:00:00")
    private LocalDateTime lastSuccessAt;

    @Schema(description = "最近一次发起同步的时间，无论成功或失败都会更新",
            example = "2026-08-25T10:00:10")
    private LocalDateTime lastAttemptAt;

    @Schema(description = "最近同步状态：NEVER-从未同步，RUNNING-同步中，SUCCESS-成功，FAILED-失败",
            example = "SUCCESS", allowableValues = {"NEVER", "RUNNING", "SUCCESS", "FAILED"})
    private String lastStatus;

    @Schema(description = "最近一次同步失败信息；同步成功后清空",
            example = "客户订单接口连接超时")
    private String errorMessage;

    @Schema(description = "同步水位记录创建时间", example = "2026-08-25T09:00:00")
    private LocalDateTime createTime;

    @Schema(description = "同步水位记录最后更新时间", example = "2026-08-25T10:00:10")
    private LocalDateTime updateTime;
}
