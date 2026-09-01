package com.kunling.scheduling.action.execution.application;

import com.kunling.scheduling.action.definition.application.ActionExecutionLock;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.execution.domain.ActionExecutionEventView;
import com.kunling.scheduling.action.execution.domain.CreateActionExecutionResult;
import com.kunling.scheduling.action.execution.domain.NewActionExecution;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 执行状态事务 seam；任何网络发送都必须发生在本接口事务之外。 */
public interface ActionExecutionStore extends ActionExecutionLock {
    CreateActionExecutionResult createIfAbsent(NewActionExecution execution);
    ActionExecutionView markDispatched(String actionInstanceId, String sessionId, String messageId, Instant sentAt);
    ActionExecutionView hold(String actionInstanceId, String code, String message, Instant now);
    /** 仅当事件是新的、顺序有效且可对外报告时返回更新后的执行事实。 */
    Optional<ActionExecutionView> applyEvent(RobotActionEvent event);
    ActionExecutionView get(String actionInstanceId);
    List<ActionExecutionEventView> getEvents(String actionInstanceId, int limit);
    Optional<ActionExecutionView> find(String actionInstanceId);
    /** 只读判断机器人是否正在执行物理 Action，不获取行锁。 */
    boolean hasActiveExecutionForRobot(String robotId);
    List<ActionExecutionView> holdInterruptedExecutions(String reasonCode, String message, Instant now);
    List<ActionExecutionView> holdActiveExecutionsForRobot(String robotId, String reasonCode,
                                                           String message, Instant now);
    List<ActionExecutionView> holdTimedOutExecutions(Instant now);
}
