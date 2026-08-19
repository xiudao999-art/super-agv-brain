package com.kunling.scheduling.action.robotbridge.application;

/** 会话生命周期通知用于驱动断线动作的查询式对账。 */
public interface RobotSessionListener {

    default void onConnected(RobotSessionView session) {
    }

    default void onDisconnected(RobotSessionView session) {
    }
}
