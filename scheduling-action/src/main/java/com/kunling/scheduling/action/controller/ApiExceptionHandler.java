package com.kunling.scheduling.action.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.definition.application.ActionNotFoundException;
import com.kunling.scheduling.action.robotbridge.application.RobotUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ActionNotFoundException.class)
    ResponseEntity<ApiError> notFound(ActionNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception, null);
    }

    @ExceptionHandler(ActionConflictException.class)
    ResponseEntity<ApiError> conflict(ActionConflictException exception) {
        return response(HttpStatus.CONFLICT, exception, null);
    }

    @ExceptionHandler(RobotUnavailableException.class)
    ResponseEntity<ApiError> unavailable(RuntimeException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, exception, null);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> badRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, exception, null);
    }

    private ResponseEntity<ApiError> response(HttpStatus status, Exception exception, Object issues) {
        return ResponseEntity.status(status)
                .body(new ApiError(exception.getClass().getSimpleName(), exception.getMessage(), issues));
    }

    @Schema(description = "接口错误响应")
    @Value
    static class ApiError {
        @Schema(description = "异常类型；Java 类型名保持英文", example = "ActionNotFoundException")
        String error;
        @Schema(description = "可直接展示的错误说明")
        String message;
        @Schema(description = "结构化校验问题；没有明细时为空")
        Object issues;
    }
}
