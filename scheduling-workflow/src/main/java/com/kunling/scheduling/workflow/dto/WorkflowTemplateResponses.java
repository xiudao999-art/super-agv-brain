package com.kunling.scheduling.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

public final class WorkflowTemplateResponses {
    private WorkflowTemplateResponses() { }

    @Data @AllArgsConstructor
    public static class Detail {
        private Long id;
        private String templateNumber;
        private String templateName;
        private String applicableObject;
        private String bpmnXml;
        private JsonNode editorData;
        private String deploymentId;
        private String processDefinitionId;
        private Integer deployedVersion;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @AllArgsConstructor
    public static class Summary {
        private Long id;
        private String templateNumber;
        private String templateName;
        private String applicableObject;
        private String processDefinitionId;
        private Integer deployedVersion;
        private LocalDateTime updatedAt;
    }
}
