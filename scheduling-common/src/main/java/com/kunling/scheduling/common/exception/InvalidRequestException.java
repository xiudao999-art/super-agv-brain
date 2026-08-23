package com.kunling.scheduling.common.exception;

/** 请求内容符合传输格式，但不满足业务输入约束。 */
public class InvalidRequestException extends BusinessException {

    public InvalidRequestException(String message) {
        super(ErrorType.BAD_REQUEST, message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(ErrorType.BAD_REQUEST, message, cause);
    }
}
