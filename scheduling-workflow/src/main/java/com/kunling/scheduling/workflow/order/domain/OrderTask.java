package com.kunling.scheduling.workflow.order.domain;

import com.baomidou.mybatisplus.annotation.TableName;
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
    /** 上游params中当前任务的items，按JSON原样保存。 */
    private String taskParams;
    private Integer templateVersion;
    private String processDefinitionId;
    private String processInstanceId;
    private OrderTaskStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long lastEventId;
    private String errorCode;
    private String errorMessage;
    private Integer attempt;
}
