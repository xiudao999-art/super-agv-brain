package com.kunling.scheduling.action.robotbridge.application;

@FunctionalInterface
public interface RobotActionEventListener {

    void onEvent(RobotActionEvent event);
}
