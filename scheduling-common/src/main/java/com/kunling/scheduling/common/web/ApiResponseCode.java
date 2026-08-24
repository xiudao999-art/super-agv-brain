package com.kunling.scheduling.common.web;

import org.springframework.http.HttpStatus;

import java.util.Arrays;

/**
 * 系统统一响应码。
 *
 * <p>所有 {@link ApiResult} 必须通过本枚举创建，禁止在业务代码中直接填写数字响应码。</p>
 */
public enum ApiResponseCode {

    SUCCESS(HttpStatus.OK, "操作成功"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "请求参数错误"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "未认证"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权访问"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    CONFLICT(HttpStatus.CONFLICT, "数据冲突"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "请求内容过大"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求内容类型不支持"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "服务暂不可用");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ApiResponseCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return httpStatus.value();
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * 将外部 HTTP 状态收敛到系统允许的固定响应码，未知状态统一按系统异常处理。
     */
    public static ApiResponseCode from(HttpStatus httpStatus) {
        if (httpStatus == null) {
            return INTERNAL_SERVER_ERROR;
        }
        return Arrays.stream(values())
                .filter(responseCode -> responseCode.httpStatus == httpStatus)
                .findFirst()
                .orElse(INTERNAL_SERVER_ERROR);
    }
}
