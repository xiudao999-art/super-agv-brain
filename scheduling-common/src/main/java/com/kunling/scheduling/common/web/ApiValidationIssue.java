package com.kunling.scheduling.common.web;

/** 参数校验失败时返回的单个字段问题。 */
public final class ApiValidationIssue {

    private final String field;
    private final String message;

    public ApiValidationIssue(String field, String message) {
        this.field = field;
        this.message = message;
    }

    public String getField() {
        return field;
    }

    public String getMessage() {
        return message;
    }
}
