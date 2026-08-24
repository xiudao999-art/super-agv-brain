package com.kunling.scheduling.workflow.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** AGV动作正常完成后推进Flowable节点的回调参数。 */
@Data
public class FlowSuccessCallbackRequest {
    @NotBlank
    private String executionId;
    /** flow业务表主键，也是下发Action时使用的actionInstanceId。 */
    @NotBlank
    private String flowId;
    /** 订单或任务业务编号。 */
    @NotBlank
    private String businessKey;
}
