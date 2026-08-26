package com.kunling.scheduling.workflow.order.api;

import com.kunling.scheduling.workflow.order.domain.OrderStatus;
import com.kunling.scheduling.workflow.order.domain.OrderTaskStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

public final class OrderResponses {
    private OrderResponses() { }

    @Data @AllArgsConstructor
    public static class Page {
        private long total;
        private long pageNum;
        private long pageSize;
        private List<OrderItem> records;
    }

    @Data @AllArgsConstructor
    public static class OrderItem {
        private Long id;
        private String upstreamOrderNo;
        private String systemOrderNo;
        private String source;
        private OrderStatus status;
        private Integer priority;
        private Integer taskCount;
        private Integer completedTaskCount;
        private String progress;
        private LocalDateTime issuedAt;
        private Date updatedAt;
    }

    @Data @AllArgsConstructor
    public static class Detail {
        private OrderItem order;
        private List<TaskItem> tasks;
        private TaskItem currentTask;
        private ExecutionConfig executionConfig;
        private String errorCode;
        private String errorMessage;
        private LocalDateTime upstreamUpdatedAt;
    }

    @Data @AllArgsConstructor
    public static class ExecutionConfig {
        private String flowNumber;
        private String flowName;
        private Long flowTemplateId;
        private String flowTemplateName;
        private String completePath;
        private String pointConfiguration;
        private String failureStrategy;
        private List<ActionItem> actions;
        private String bpmnXml;
        private List<BpmnProcess> bpmnProcesses;
    }

    @Data @AllArgsConstructor
    public static class BpmnProcess {
        private String processId;
        private String processName;
        private List<BpmnNode> nodes;
        private List<BpmnFlow> flows;
    }

    @Data @AllArgsConstructor
    public static class BpmnNode {
        private String id;
        private String name;
        private String type;
        private String parentSubProcessId;
        private Double x;
        private Double y;
        private Double width;
        private Double height;
    }

    @Data @AllArgsConstructor
    public static class BpmnFlow {
        private String id;
        private String name;
        private String sourceRef;
        private String targetRef;
        private String conditionExpression;
        private String parentSubProcessId;
    }

    @Data @AllArgsConstructor
    public static class ActionItem {
        private Long nodeId;
        private String sequence;
        private Integer sort;
        private String actionName;
        private String resource;
        private String nodeName;
        private String nodeCode;
        private String status;
        private String completionEvidence;
        private String completionCriteria;
        private String failureStrategy;
    }

    @Data @AllArgsConstructor
    public static class TaskItem {
        private Long id;
        private String taskNumber;
        private Integer taskSeq;
        private String taskName;
        private String flowNumber;
        private OrderTaskStatus status;
        private String currentStep;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private Date updatedAt;
        private String errorMessage;
    }

    @Data @AllArgsConstructor
    public static class TaskSummary {
        private Long orderId;
        private int taskCount;
        private int completedTaskCount;
        private TaskItem currentTask;
    }
}
