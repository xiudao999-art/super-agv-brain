package com.kunling.scheduling.action.execution.infrastructure;

import com.kunling.scheduling.action.execution.domain.ActionExecutionState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "action_execution")
public class ActionExecutionEntity {

    @Id
    @Column(name = "action_instance_id", length = 128, nullable = false)
    private String actionInstanceId;

    @Column(name = "robot_id", length = 128, nullable = false)
    private String robotId;

    @Column(name = "action_key", length = 128, nullable = false)
    private String actionKey;

    @Column(name = "action_version", length = 32, nullable = false)
    private String actionVersion;

    @Column(name = "workflow_instance_id", length = 128)
    private String workflowInstanceId;

    @Column(name = "workflow_node_instance_id", length = 128)
    private String workflowNodeInstanceId;

    @Column(name = "plan_hash", length = 64, nullable = false)
    private String planHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private ActionExecutionState state;

    @Column(name = "physical_result_known", nullable = false)
    private boolean physicalResultKnown;

    @Column(name = "current_node_id", length = 1000)
    private String currentNodeId;

    @Lob
    @Column(name = "input_json", nullable = false, columnDefinition = "longtext")
    private String inputJson;

    @Lob
    @Column(name = "context_json", nullable = false, columnDefinition = "longtext")
    private String contextJson;

    @Lob
    @Column(name = "result_json", columnDefinition = "longtext")
    private String resultJson;

    @Lob
    @Column(name = "error_json", columnDefinition = "longtext")
    private String errorJson;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ActionExecutionEntity() {
    }

    public ActionExecutionEntity(String actionInstanceId, String robotId, String actionKey, String actionVersion,
                                 String workflowInstanceId, String workflowNodeInstanceId,
                                 String planHash, String inputJson, String contextJson, Instant now) {
        this.actionInstanceId = actionInstanceId;
        this.robotId = robotId;
        this.actionKey = actionKey;
        this.actionVersion = actionVersion;
        this.workflowInstanceId = workflowInstanceId;
        this.workflowNodeInstanceId = workflowNodeInstanceId;
        this.planHash = planHash;
        this.state = ActionExecutionState.ACCEPTED;
        this.physicalResultKnown = true;
        this.inputJson = inputJson;
        this.contextJson = contextJson;
        this.cancelRequested = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getActionInstanceId() { return actionInstanceId; }
    public String getRobotId() { return robotId; }
    public String getActionKey() { return actionKey; }
    public String getActionVersion() { return actionVersion; }
    public String getWorkflowInstanceId() { return workflowInstanceId; }
    public String getWorkflowNodeInstanceId() { return workflowNodeInstanceId; }
    public String getPlanHash() { return planHash; }
    public ActionExecutionState getState() { return state; }
    public boolean isPhysicalResultKnown() { return physicalResultKnown; }
    public String getCurrentNodeId() { return currentNodeId; }
    public String getInputJson() { return inputJson; }
    public String getContextJson() { return contextJson; }
    public String getResultJson() { return resultJson; }
    public String getErrorJson() { return errorJson; }
    public boolean isCancelRequested() { return cancelRequested; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void running(String nodeId, Instant now) {
        state = ActionExecutionState.RUNNING;
        currentNodeId = nodeId;
        updatedAt = now;
    }

    public void complete(String resultJson, Instant now) {
        state = ActionExecutionState.PHYSICAL_DONE;
        physicalResultKnown = true;
        currentNodeId = null;
        this.resultJson = resultJson;
        updatedAt = now;
        completedAt = now;
    }

    public void fail(String errorJson, Instant now) {
        state = ActionExecutionState.FAILED;
        physicalResultKnown = true;
        this.errorJson = errorJson;
        updatedAt = now;
        completedAt = now;
    }

    public void hold(String errorJson, Instant now) {
        state = ActionExecutionState.UNKNOWN_HOLD;
        physicalResultKnown = false;
        this.errorJson = errorJson;
        updatedAt = now;
        completedAt = now;
    }

    public void requestCancel(Instant now) {
        cancelRequested = true;
        updatedAt = now;
    }

    public void cancel(Instant now) {
        state = ActionExecutionState.CANCELLED;
        physicalResultKnown = true;
        currentNodeId = null;
        updatedAt = now;
        completedAt = now;
    }
}
