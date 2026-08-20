package com.kunling.scheduling.action.execution.application;

import com.kunling.scheduling.action.definition.application.ActionExecutionLock;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
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
    ActionExecutionView applyEvent(RobotActionEvent event);
    ActionExecutionView get(String actionInstanceId);
    Optional<ActionExecutionView> find(String actionInstanceId);
    List<ActionExecutionView> holdInterruptedExecutions(String reasonCode, String message, Instant now);
    List<ActionExecutionView> holdActiveExecutionsForRobot(String robotId, String reasonCode,
                                                           String message, Instant now);
    List<ActionExecutionView> findHeldExecutionsForRobot(String robotId);
    List<ActionExecutionView> holdTimedOutExecutions(Instant now);
}
