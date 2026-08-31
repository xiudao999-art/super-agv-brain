package com.kunling.scheduling.action.robotbridge.application;

import java.util.List;
import java.util.Optional;

/** Action 模块可依赖的小接口，不暴露 Socket、线程或协议 DTO。 */
public interface RobotActionTransport {

    DispatchReceipt dispatch(RobotActionCommand command);

    Optional<RobotSessionView> findSession(String robotId);

    List<RobotSessionView> listSessions();
}
