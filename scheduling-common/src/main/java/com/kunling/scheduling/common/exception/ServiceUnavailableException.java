package com.kunling.scheduling.common.exception;

/** 依赖的设备或外部服务当前不可用。 */
public class ServiceUnavailableException extends BusinessException {

    public ServiceUnavailableException(String message) {
        super(ErrorType.SERVICE_UNAVAILABLE, message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(ErrorType.SERVICE_UNAVAILABLE, message, cause);
    }
}
