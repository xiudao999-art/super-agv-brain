package com.kunling.scheduling.common.web;

import com.kunling.scheduling.common.exception.BusinessException;
import com.kunling.scheduling.common.exception.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * 全系统唯一的 HTTP 异常出口。
 *
 * <p>业务异常按稳定错误类型映射；未知异常只记录服务端日志，不向调用方暴露内部堆栈。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INTERNAL_ERROR_MESSAGE = "系统内部错误";

    private final List<ApiExceptionMapper> exceptionMappers;

    public GlobalExceptionHandler(List<ApiExceptionMapper> exceptionMappers) {
        this.exceptionMappers = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(exceptionMappers, "异常映射器列表不能为空")));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Object>> handleBusinessException(BusinessException exception) {
        return response(exception.getErrorType(), exception.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Object>> handleMethodValidation(MethodArgumentNotValidException exception) {
        List<ApiValidationIssue> issues = new ArrayList<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                issues.add(new ApiValidationIssue(error.getField(), error.getDefaultMessage())));
        return response(ErrorType.BAD_REQUEST, "请求参数校验失败", issues);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResult<Object>> handleBinding(BindException exception) {
        List<ApiValidationIssue> issues = new ArrayList<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                issues.add(new ApiValidationIssue(error.getField(), error.getDefaultMessage())));
        return response(ErrorType.BAD_REQUEST, "请求参数绑定失败", issues);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult<Object>> handleConstraintViolation(ConstraintViolationException exception) {
        List<ApiValidationIssue> issues = new ArrayList<>();
        exception.getConstraintViolations().forEach(violation -> issues.add(new ApiValidationIssue(
                violation.getPropertyPath().toString(), violation.getMessage())));
        return response(ErrorType.BAD_REQUEST, "请求参数校验失败", issues);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Object>> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return response(ErrorType.BAD_REQUEST, "请求内容格式错误", null);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, ServletRequestBindingException.class})
    public ResponseEntity<ApiResult<Object>> handleRequestBinding(Exception exception) {
        return response(ErrorType.BAD_REQUEST, "请求参数格式错误", null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResult<Object>> handleResponseStatus(ResponseStatusException exception) {
        String message = exception.getReason() == null ? exception.getStatus().getReasonPhrase() : exception.getReason();
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResult.failure(exception.getStatus().value(), message, null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult<Object>> handleUnsupportedMethod(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResult.failure(HttpStatus.METHOD_NOT_ALLOWED.value(), "请求方法不支持", null));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResult<Object>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResult.failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "请求内容类型不支持", null));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResult<Object>> handleLegacyBadRequest(RuntimeException exception) {
        return response(ErrorType.BAD_REQUEST, exception.getMessage(), null);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResult<Object>> handleLegacyNotFound(NoSuchElementException exception) {
        return response(ErrorType.NOT_FOUND, exception.getMessage(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResult<Object>> handleDataConflict(DataIntegrityViolationException exception) {
        return response(ErrorType.CONFLICT, "数据已被其他配置引用或发生并发冲突", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Object>> handleUnexpectedException(Exception exception) {
        for (ApiExceptionMapper exceptionMapper : exceptionMappers) {
            try {
                Optional<ApiExceptionMapping> mapping = exceptionMapper.map(exception);
                if (mapping.isPresent()) {
                    ApiExceptionMapping value = mapping.get();
                    return response(value.getErrorType(), value.getMessage(), value.getIssues());
                }
            } catch (RuntimeException mappingException) {
                // 单个扩展映射器失败不能破坏全局兜底响应。
                LOGGER.error("接口异常映射器执行失败: {}", exceptionMapper.getClass().getName(), mappingException);
            }
        }
        LOGGER.error("未处理的接口异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.failure(HttpStatus.INTERNAL_SERVER_ERROR.value(), INTERNAL_ERROR_MESSAGE, null));
    }

    private ResponseEntity<ApiResult<Object>> response(ErrorType errorType, String message, Object issues) {
        HttpStatus status = toHttpStatus(errorType);
        return ResponseEntity.status(status)
                .body(ApiResult.failure(status.value(), safeMessage(message), issues));
    }

    private HttpStatus toHttpStatus(ErrorType errorType) {
        switch (errorType) {
            case BAD_REQUEST:
                return HttpStatus.BAD_REQUEST;
            case NOT_FOUND:
                return HttpStatus.NOT_FOUND;
            case CONFLICT:
                return HttpStatus.CONFLICT;
            case SERVICE_UNAVAILABLE:
                return HttpStatus.SERVICE_UNAVAILABLE;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private String safeMessage(String message) {
        return message == null || message.trim().isEmpty() ? "请求处理失败" : message;
    }
}
