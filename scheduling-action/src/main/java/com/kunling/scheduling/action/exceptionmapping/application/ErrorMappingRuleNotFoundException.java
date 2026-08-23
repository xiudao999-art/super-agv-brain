package com.kunling.scheduling.action.exceptionmapping.application;

public class ErrorMappingRuleNotFoundException extends RuntimeException {
    public ErrorMappingRuleNotFoundException(String message) {
        super(message);
    }
}
