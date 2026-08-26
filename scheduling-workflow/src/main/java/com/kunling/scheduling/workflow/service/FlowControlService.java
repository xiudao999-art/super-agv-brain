package com.kunling.scheduling.workflow.service;

import com.kunling.scheduling.workflow.dto.FlowStartRequest;
import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.enums.StartTypeEnum;

public interface FlowControlService {
    boolean start(FlowStartRequest request);

    /** 终止指定运行实例，由Flowable级联清理ACT_RU_EXECUTION及关联运行数据。 */
    boolean clear(String processInstanceId, String reason);

//    boolean processCallback(FlowStartRequest request);

    /** 激活已挂起的任务流程，并重新下发当前活动节点。 */
    boolean resumeTask(Long taskId);

//    boolean processFailure(FlowFailureCallbackRequest request);

    boolean dispatchDownstreamAction(String processInstanceId,
                                     Long taskId,
                                     WorkflowResponses.ActiveNode activeNode,
                                     StartTypeEnum startType);
}
