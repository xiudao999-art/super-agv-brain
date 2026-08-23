package com.kunling.scheduling.agvflow.domain.dto;

import lombok.Data;

@Data
public class StatusChangedDto {
    private String nodeState;

    private String eventCode;

    private String workflowInstanceId;
    /** 状态机节点实例标识；设备联调场景允许为空。 */
    private  String workflowNodeInstanceId;
}
