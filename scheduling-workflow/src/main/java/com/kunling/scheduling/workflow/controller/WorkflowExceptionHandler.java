package com.kunling.scheduling.workflow.controller;

import org.flowable.common.engine.api.FlowableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice(basePackages = "com.kunling.scheduling.workflow")
public class WorkflowExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, FlowableException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Collections.singletonMap("message", exception.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Collections.singletonMap("message", exception.getMessage()));
    }
}
