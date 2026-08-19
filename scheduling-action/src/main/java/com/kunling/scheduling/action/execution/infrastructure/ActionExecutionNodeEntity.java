package com.kunling.scheduling.action.execution.infrastructure;

import com.kunling.scheduling.action.execution.domain.ActionNodeState;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "action_execution_node")
public class ActionExecutionNodeEntity {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "action_instance_id", length = 128, nullable = false)
    private String actionInstanceId;

    @Column(name = "node_ordinal", nullable = false)
    private int nodeOrdinal;

    @Column(name = "execution_node_id", length = 1000, nullable = false)
    private String executionNodeId;

    @Lob
    @Column(name = "source_path", nullable = false, columnDefinition = "longtext")
    private String sourcePath;

    @Column(name = "capability_key", length = 128, nullable = false)
    private String capabilityKey;

    @Column(name = "capability_contract_hash", length = 64)
    private String capabilityContractHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private ActionNodeState state;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "consume_id", length = 128, nullable = false)
    private String consumeId;

    @Lob
    @Column(name = "resolved_input_json", nullable = false, columnDefinition = "longtext")
    private String resolvedInputJson;

    @Lob
    @Column(name = "output_json", columnDefinition = "longtext")
    private String outputJson;

    @Lob
    @Column(name = "evidence_json", columnDefinition = "longtext")
    private String evidenceJson;

    @Lob
    @Column(name = "error_json", columnDefinition = "longtext")
    private String errorJson;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ActionExecutionNodeEntity() {
    }

    public ActionExecutionNodeEntity(String id, String actionInstanceId, int nodeOrdinal,
                                     String executionNodeId, String sourcePath, String capabilityKey,
                                     String capabilityContractHash, String consumeId) {
        this.id = id;
        this.actionInstanceId = actionInstanceId;
        this.nodeOrdinal = nodeOrdinal;
        this.executionNodeId = executionNodeId;
        this.sourcePath = sourcePath;
        this.capabilityKey = capabilityKey;
        this.capabilityContractHash = capabilityContractHash;
        this.state = ActionNodeState.PENDING;
        this.attempt = 0;
        this.consumeId = consumeId;
        this.resolvedInputJson = "{}";
    }

    public String getId() { return id; }
    public String getActionInstanceId() { return actionInstanceId; }
    public int getNodeOrdinal() { return nodeOrdinal; }
    public String getExecutionNodeId() { return executionNodeId; }
    public String getSourcePath() { return sourcePath; }
    public String getCapabilityKey() { return capabilityKey; }
    public String getCapabilityContractHash() { return capabilityContractHash; }
    public ActionNodeState getState() { return state; }
    public int getAttempt() { return attempt; }
    public String getConsumeId() { return consumeId; }
    public String getResolvedInputJson() { return resolvedInputJson; }
    public String getOutputJson() { return outputJson; }
    public String getEvidenceJson() { return evidenceJson; }
    public String getErrorJson() { return errorJson; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void start(String resolvedInputJson, Instant now) {
        state = ActionNodeState.RUNNING;
        attempt++;
        this.resolvedInputJson = resolvedInputJson;
        startedAt = now;
    }

    public void succeed(String outputJson, String evidenceJson, Instant now) {
        state = ActionNodeState.SUCCEEDED;
        this.outputJson = outputJson;
        this.evidenceJson = evidenceJson;
        completedAt = now;
    }

    public void fail(String errorJson, String evidenceJson, Instant now) {
        state = ActionNodeState.FAILED;
        this.errorJson = errorJson;
        this.evidenceJson = evidenceJson;
        completedAt = now;
    }

    public void hold(String errorJson, String evidenceJson, Instant now) {
        state = ActionNodeState.HOLDING;
        this.errorJson = errorJson;
        this.evidenceJson = evidenceJson;
        completedAt = now;
    }

    public void cancel(Instant now) {
        if (state == ActionNodeState.PENDING) {
            state = ActionNodeState.CANCELLED;
            completedAt = now;
        }
    }
}
