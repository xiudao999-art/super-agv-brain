package com.kunling.scheduling.workflow.service;

import com.kunling.scheduling.workflow.dto.FlowStartRequest;

public interface FlowControlService {
    boolean start(FlowStartRequest request);

    /** 终止指定运行实例，由Flowable级联清理ACT_RU_EXECUTION及关联运行数据。 */
    boolean clear(String processInstanceId, String reason);

    boolean processCallback(FlowStartRequest request);
}
