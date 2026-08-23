package com.kunling.scheduling.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

public final class WorkflowResponses {
    private WorkflowResponses() { }

    @Data @AllArgsConstructor
    public static class Definition {
        private String id; private String key; private String name; private int version;
        private String deploymentId; private String resourceName; private String category;
    }

    @Data @AllArgsConstructor
    public static class Instance {
        private String id; private String processDefinitionId; private String businessKey;
        private String state; private boolean suspended; private Date startTime; private Date endTime;
        private String deleteReason;
    }

    @Data @AllArgsConstructor
    public static class ActiveNode {
        private String executionId; private String activityId; private String processInstanceId;
        private boolean suspended;
    }

    @Data @AllArgsConstructor
    public static class HistoryNode {
        private String id; private String activityId; private String activityName; private String activityType;
        private String executionId; private Date startTime; private Date endTime; private Long durationMillis;
        private String assignee;
    }

    @Data @AllArgsConstructor
    public static class UserTask {
        private String id; private String name; private String taskDefinitionKey; private String assignee;
        private String processInstanceId; private Date createTime;
    }
}
