package com.kunling.scheduling.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * 全系统统一的 HTTP 接口返回模型。
 *
 * <p>字段保持精简：{@code code} 与 HTTP 状态码一致，{@code message} 用于展示处理结果，
 * {@code data} 承载业务数据或校验明细。成功和失败均使用同一结构，避免调用方维护两套解析逻辑。</p>
 *
 * @param <T> 响应数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ApiResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final String DEFAULT_SUCCESS_MESSAGE = "操作成功";

    private final int code;
    private final String message;
    private final T data;

    private ApiResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResult<T> success(int code, T data) {
        return new ApiResult<>(code, DEFAULT_SUCCESS_MESSAGE, data);
    }

    public static ApiResult<Void> success(int code) {
        return success(code, null);
    }

    public static <T> ApiResult<T> failure(int code, String message, T data) {
        return new ApiResult<>(code, message, data);
    }

    public static ApiResult<Void> failure(int code, String message) {
        return failure(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
