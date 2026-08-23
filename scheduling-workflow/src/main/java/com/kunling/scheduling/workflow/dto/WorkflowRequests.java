package com.kunling.scheduling.workflow.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.Map;

public final class WorkflowRequests {
    private WorkflowRequests() { }

    @Data
    public static class DeployDefinition {
        @NotBlank private String name;
        private String resourceName;
        @NotBlank private String bpmnXml;
        private String category;
    }

    @Data
    public static class StartInstance {
        private String processDefinitionId;
        private String processDefinitionKey;
        private String businessKey;
        private Map<String, Object> variables;
    }

    @Data
    public static class TriggerExecution {
        private Map<String, Object> variables;
    }

    @Data
    public static class ClaimTask {
        @NotBlank private String assignee;
    }

    @Data
    public static class CompleteTask {
        private Map<String, Object> variables;
    }

    @Data
    public static class TerminateInstance {
        private String reason;
    }
}
