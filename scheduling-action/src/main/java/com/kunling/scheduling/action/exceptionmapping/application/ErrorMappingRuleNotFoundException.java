package com.kunling.scheduling.action.exceptionmapping.application;

import com.kunling.scheduling.common.exception.ResourceNotFoundException;

public class ErrorMappingRuleNotFoundException extends ResourceNotFoundException {
    public ErrorMappingRuleNotFoundException(String message) {
        super(message);
    }
}
