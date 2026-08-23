package com.kunling.scheduling.action.robotbridge.application;

import com.kunling.scheduling.common.exception.ServiceUnavailableException;

public class RobotUnavailableException extends ServiceUnavailableException {

    public RobotUnavailableException(String message) {
        super(message);
    }

    public RobotUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
