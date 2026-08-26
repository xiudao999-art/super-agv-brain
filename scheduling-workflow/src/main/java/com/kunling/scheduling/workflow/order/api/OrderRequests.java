package com.kunling.scheduling.workflow.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class OrderRequests {
    private OrderRequests() {
    }

    @Data
    @Schema(description = "手工创建订单请求")
    public static class Create {
        @NotBlank
        @Schema(description = "订单来源", example = "MANUAL")
        private String source;

        @NotBlank
        @Schema(description = "业务订单号，同一来源下不能重复", example = "MANUAL-20260826-001")
        private String upstreamOrderNo;

        @NotNull
        @Min(1)
        @Max(4)
        @Schema(description = "优先级：1最高，4最低", example = "1")
        private Integer priority;

        @Schema(description = "订单下发时间，不传使用当前时间")
        private LocalDateTime issuedAt;

        @Valid
        @NotEmpty
        private List<Task> tasks = new ArrayList<>();
    }

    @Data
    @Schema(description = "手工创建的订单任务")
    public static class Task {
        @NotNull
        @Min(1)
        @Schema(description = "订单内执行顺序", example = "1")
        private Integer taskSeq;

        @NotBlank
        @Schema(description = "任务名称", example = "移动到仓库位")
        private String taskName;

        @NotNull
        @Schema(description = "flow_template主键", example = "5")
        private Long flowTemplateId;
    }
}
