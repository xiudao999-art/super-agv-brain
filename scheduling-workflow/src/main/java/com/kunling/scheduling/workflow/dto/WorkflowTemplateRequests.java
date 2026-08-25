package com.kunling.scheduling.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import javax.validation.constraints.NotBlank;

public final class WorkflowTemplateRequests {
    private WorkflowTemplateRequests() { }

    @Data
    public static class Save {
        @NotBlank private String templateName;
        private String applicableObject;
        @NotBlank private String bpmnXml;
        private JsonNode editorData;
        private Long id;
    }
}
