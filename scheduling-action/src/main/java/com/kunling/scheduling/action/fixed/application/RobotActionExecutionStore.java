package com.kunling.scheduling.action.fixed.application;

import com.kunling.scheduling.action.fixed.domain.CreateRobotActionExecutionResult;
import com.kunling.scheduling.action.fixed.domain.NewRobotActionExecution;
import com.kunling.scheduling.action.fixed.domain.RobotActionExecutionView;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 执行状态事务边界；网络发送不得发生在本接口的数据库事务内部。 */
public interface RobotActionExecutionStore {

    CreateRobotActionExecutionResult createIfAbsent(NewRobotActionExecution execution);

    RobotActionExecutionView markDispatched(String actionInstanceId, String sessionId,
                                            String messageId, Instant sentAt);

    RobotActionExecutionView hold(String actionInstanceId, String code, String message, Instant now);

    RobotActionExecutionView applyEvent(RobotActionEvent event);

    RobotActionExecutionView get(String actionInstanceId);

    Optional<RobotActionExecutionView> find(String actionInstanceId);

    List<RobotActionExecutionView> holdInterruptedExecutions(String reasonCode, String message, Instant now);

    List<RobotActionExecutionView> holdActiveExecutionsForRobot(String robotId, String reasonCode,
                                                                String message, Instant now);

    List<RobotActionExecutionView> findHeldExecutionsForRobot(String robotId);

    List<RobotActionExecutionView> holdTimedOutExecutions(Instant now);
}
