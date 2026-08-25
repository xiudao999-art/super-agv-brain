package com.kunling.scheduling.workflow.order.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.kunling.scheduling.workflow.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_task")
public class OrderTask extends BaseEntity {
    private Long orderId;
    private Integer taskSeq;
    private String taskName;
    private String flowNumber;
    private Long flowTemplateId;
    private OrderTaskStatus status;
    private String currentStep;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorCode;
    private String errorMessage;
    @Version
    private Integer version;
}
