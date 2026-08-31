package com.kunling.scheduling.workflow.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowActionBindingValidatorTest {

    private final WorkflowActionBindingValidator validator =
            new WorkflowActionBindingValidator();

    @Test
    void acceptsReceiveTaskBoundToActionDefinition() {
        assertDoesNotThrow(() -> validator.validate(process(
                "<receiveTask id=\"move\" flowable:actionDefinitionId=\"action-definition-1\"/>")));
    }

    @Test
    void rejectsReceiveTaskWithoutActionDefinition() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(process("<receiveTask id=\"move\"/>")));

        assertTrue(exception.getMessage().contains("move"));
    }

    @Test
    void doesNotRequireActionBindingForManualTask() {
        assertDoesNotThrow(() -> validator.validate(process("<userTask id=\"manual\"/>")));
    }

    private String process(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" "
                + "xmlns:flowable=\"http://flowable.org/bpmn\" "
                + "targetNamespace=\"http://kunling.com/workflow\">"
                + "<process id=\"test\" isExecutable=\"true\">" + body + "</process>"
                + "</definitions>";
    }
}
