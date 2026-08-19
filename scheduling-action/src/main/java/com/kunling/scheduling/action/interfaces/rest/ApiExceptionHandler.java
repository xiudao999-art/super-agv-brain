package com.kunling.scheduling.action.interfaces.rest;

import com.kunling.scheduling.action.definition.application.ActionCompilationException;
import com.kunling.scheduling.action.definition.application.ActionConflictException;
import com.kunling.scheduling.action.definition.application.ActionNotFoundException;
import com.kunling.scheduling.action.upstream.application.UpstreamUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

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

    @ExceptionHandler(ActionCompilationException.class)
    ResponseEntity<ApiError> compilation(ActionCompilationException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, exception, exception.getIssues());
    }

    @ExceptionHandler(UpstreamUnavailableException.class)
    ResponseEntity<ApiError> unavailable(UpstreamUnavailableException exception) {
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

    record ApiError(String error, String message, Object issues) {
    }
}
