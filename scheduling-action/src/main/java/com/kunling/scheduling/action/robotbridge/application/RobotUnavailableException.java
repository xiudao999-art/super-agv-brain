package com.kunling.scheduling.action.robotbridge.application;

public class RobotUnavailableException extends RuntimeException {

    public RobotUnavailableException(String message) {
        super(message);
    }

    public RobotUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
