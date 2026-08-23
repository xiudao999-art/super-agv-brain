package com.kunling.scheduling.action.exceptionmapping.application;

import com.kunling.scheduling.common.exception.ConflictException;

public class ErrorMappingRuleConflictException extends ConflictException {
    public ErrorMappingRuleConflictException(String message) {
        super(message);
    }
}
