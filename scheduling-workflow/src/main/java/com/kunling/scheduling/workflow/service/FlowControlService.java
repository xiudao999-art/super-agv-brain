package com.kunling.scheduling.workflow.service;

public interface FlowControlService {
    boolean start(String processDefinitionId,Long businessKey,Long template);

    /** 终止指定运行实例，由Flowable级联清理ACT_RU_EXECUTION及关联运行数据。 */
    boolean clear(String processInstanceId, String reason);

    boolean processCallback(String executionId, String taskId, String businessKey);
}
