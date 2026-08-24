package com.kunling.scheduling.action.definition.application;

import com.kunling.scheduling.common.exception.ResourceNotFoundException;

public class ActionNotFoundException extends ResourceNotFoundException {

    public ActionNotFoundException(String message) {
        super(message);
    }
}
