package com.kunling.scheduling.common.exception;

import java.util.Objects;

/** 所有可预期业务异常的公共基类。 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorType errorType;

    protected BusinessException(ErrorType errorType, String message) {
        super(message);
        this.errorType = Objects.requireNonNull(errorType, "错误类型不能为空");
    }

    protected BusinessException(ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = Objects.requireNonNull(errorType, "错误类型不能为空");
    }

    public ErrorType getErrorType() {
        return errorType;
    }
}
