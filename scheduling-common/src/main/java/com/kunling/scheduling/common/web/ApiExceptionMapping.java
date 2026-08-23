package com.kunling.scheduling.common.web;

import com.kunling.scheduling.common.exception.ErrorType;

import java.util.Objects;

/** 业务模块将第三方异常转换成统一错误语义时使用的结果对象。 */
public final class ApiExceptionMapping {

    private final ErrorType errorType;
    private final String message;
    private final Object issues;

    public ApiExceptionMapping(ErrorType errorType, String message, Object issues) {
        this.errorType = Objects.requireNonNull(errorType, "错误类型不能为空");
        this.message = message;
        this.issues = issues;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public String getMessage() {
        return message;
    }

    public Object getIssues() {
        return issues;
    }
}
