package com.kunling.scheduling.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.Objects;

/**
 * 全系统统一的 HTTP 接口返回模型。
 *
 * <p>字段保持精简：{@code code} 表示业务结果码，{@code message} 用于展示处理结果，
 * {@code data} 承载业务数据或校验明细。HTTP 状态由响应本身表达，不要求与业务码相同。</p>
 *
 * @param <T> 响应数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ApiResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "系统固定响应码", example = "200",
            allowableValues = {"200", "400", "401", "403", "404", "405", "409", "413", "415", "500", "503"})
    private final int code;
    @Schema(description = "响应说明", example = "操作成功")
    private final String message;
    /** 即使没有返回数据也保留该字段，保证客户端始终收到稳定的三字段结构。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "业务数据；没有业务数据时为 null", nullable = true)
    private final T data;

    private ApiResult(ApiResponseCode responseCode, String message, T data) {
        ApiResponseCode requiredCode = Objects.requireNonNull(responseCode, "响应码不能为空");
        this.code = requiredCode.getCode();
        this.message = message == null || message.trim().isEmpty()
                ? requiredCode.getDefaultMessage()
                : message;
        this.data = data;
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(ApiResponseCode.SUCCESS, null, data);
    }

    public static ApiResult<Void> success() {
        return success(null);
    }

    public static <T> ApiResult<T> created(T data) {
        return success(data);
    }

    public static <T> ApiResult<T> accepted(T data) {
        return success(data);
    }

    public static <T> ApiResult<T> failure(ApiResponseCode responseCode, String message, T data) {
        return new ApiResult<>(responseCode, message, data);
    }

    public static ApiResult<Void> failure(ApiResponseCode responseCode, String message) {
        return failure(responseCode, message, null);
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
