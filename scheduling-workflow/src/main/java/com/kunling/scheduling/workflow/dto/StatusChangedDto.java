package com.kunling.scheduling.workflow.dto;

import lombok.Data;

@Data
public class StatusChangedDto {
    private String nodeState;
    /** Action最终报告是否成功。 */
    private Boolean success;
    private String actionInstanceId;
    private String actionKey;
    private String physicalOutcome;
    private String businessCode;
    private String reasonCode;

    private String eventCode;

    private String workflowInstanceId;
    /** 状态机节点实例标识；设备联调场景允许为空。 */
    private  String workflowNodeInstanceId;
}
