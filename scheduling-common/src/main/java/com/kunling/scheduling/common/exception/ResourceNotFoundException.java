package com.kunling.scheduling.common.exception;

/** 请求访问的业务资源不存在。 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(ErrorType.NOT_FOUND, message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(ErrorType.NOT_FOUND, message, cause);
    }
}
