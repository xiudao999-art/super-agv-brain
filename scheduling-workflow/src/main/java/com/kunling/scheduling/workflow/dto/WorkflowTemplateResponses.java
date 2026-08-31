package com.kunling.scheduling.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Date;

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
        private String status;
        private String statusDescription;
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

    /** 流程模板列表页返回对象。 */
    @Data @AllArgsConstructor
    public static class Page {
        private long total;
        private long pageNum;
        private long pageSize;
        private List<PageItem> records;
    }

    /** 模板列表中的一行数据。 */
    @Data @AllArgsConstructor
    public static class PageItem {
        private Long id;
        private String templateNumber;
        private String templateName;
        private List<String> actionSequence;
        private String actionSequenceText;
        private String applicableObject;
        private Integer version;
        private String status;
        private String statusDescription;
        private String processDefinitionId;
        private LocalDateTime updatedAt;
    }

    /** “流程列表”页分页数据。 */
    @Data @AllArgsConstructor
    public static class FlowPage {
        private long total;
        private long pageNum;
        private long pageSize;
        private List<FlowPageItem> records;
    }

    /** “流程列表”页单行数据。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowPageItem {
        private Long id;
        private String flowNumber;
        private String flowName;
        private Long templateId;
        private String templateName;
        private Integer templateNodeCount;
//        private String processDefinitionId;
        private Date updatedAt;
    }
}
