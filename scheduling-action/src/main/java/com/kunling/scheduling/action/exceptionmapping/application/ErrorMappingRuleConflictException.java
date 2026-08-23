package com.kunling.scheduling.action.exceptionmapping.application;

public class ErrorMappingRuleConflictException extends RuntimeException {
    public ErrorMappingRuleConflictException(String message) {
        super(message);
    }
}
