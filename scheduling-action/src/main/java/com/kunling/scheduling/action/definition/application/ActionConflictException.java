package com.kunling.scheduling.action.definition.application;

import com.kunling.scheduling.common.exception.ConflictException;

public class ActionConflictException extends ConflictException {

    public ActionConflictException(String message) {
        super(message);
    }
}
