package com.kunling.scheduling.common.exception;

/**
 * 与传输协议无关的业务错误分类。
 *
 * <p>业务模块只表达错误语义，HTTP 状态码由全局异常处理器统一映射。</p>
 */
public enum ErrorType {
    BAD_REQUEST,
    NOT_FOUND,
    CONFLICT,
    SERVICE_UNAVAILABLE
}
