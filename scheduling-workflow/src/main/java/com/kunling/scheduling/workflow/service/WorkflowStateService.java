package com.kunling.scheduling.workflow.service;

import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.enums.ProcessState;

import java.util.Map;

/**
 * Flowable流程实例状态服务。
 *
 * <p>只通过Flowable公开API改变引擎状态，禁止直接修改ACT_RU_*、ACT_HI_*表。</p>
 */
public interface WorkflowStateService {

    /** 查询运行中或已结束的流程实例状态。 */
    WorkflowResponses.Instance get(String processInstanceId);

    /** 查询标准化流程状态：RUNNING/SUSPENDED/COMPLETED/TERMINATED。 */
    ProcessState getState(String processInstanceId);

    /** 挂起运行中的流程；已经挂起时幂等返回。 */
    WorkflowResponses.Instance suspend(String processInstanceId);

    /** 恢复已挂起的流程；已经活动时幂等返回。 */
    WorkflowResponses.Instance activate(String processInstanceId);

    /**
     * 完成ReceiveTask等可触发的等待节点并推进流程。
     * 如果推进到endEvent，返回状态为COMPLETED。
     */
    WorkflowResponses.Instance completeExecution(String executionId, Map<String, Object> variables);

    /** 强制终止运行中或挂起的流程。 */
    WorkflowResponses.Instance terminate(String processInstanceId, String reason);

    /** 流程是否已经正常完成。 */
    boolean isCompleted(String processInstanceId);

    /** 流程是否已经结束（正常完成或强制终止）。 */
    boolean isEnded(String processInstanceId);
}
