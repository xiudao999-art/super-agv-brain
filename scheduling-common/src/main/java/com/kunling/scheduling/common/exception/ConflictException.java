package com.kunling.scheduling.common.exception;

/** 当前资源状态与请求操作冲突。 */
public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(ErrorType.CONFLICT, message);
    }

    public ConflictException(String message, Throwable cause) {
        super(ErrorType.CONFLICT, message, cause);
    }
}
