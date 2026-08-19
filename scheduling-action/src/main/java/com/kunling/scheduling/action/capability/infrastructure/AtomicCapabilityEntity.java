package com.kunling.scheduling.action.capability.infrastructure;

import com.kunling.scheduling.action.capability.domain.CapabilityRetrySafety;
import com.kunling.scheduling.action.capability.domain.CapabilitySideEffect;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "atomic_capability")
public class AtomicCapabilityEntity {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "capability_key", length = 128, nullable = false)
    private String capabilityKey;

    @Column(name = "contract_hash", length = 64, nullable = false)
    private String contractHash;

    @Lob
    @Column(name = "input_schema_json", nullable = false, columnDefinition = "longtext")
    private String inputSchemaJson;

    @Lob
    @Column(name = "output_schema_json", nullable = false, columnDefinition = "longtext")
    private String outputSchemaJson;

    @Lob
    @Column(name = "resources_json", nullable = false, columnDefinition = "longtext")
    private String resourcesJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "side_effect", length = 32, nullable = false)
    private CapabilitySideEffect sideEffect;

    @Enumerated(EnumType.STRING)
    @Column(name = "retry_safety", length = 32, nullable = false)
    private CapabilityRetrySafety retrySafety;

    @Column(name = "safety_critical", nullable = false)
    private boolean safetyCritical;

    @Column(name = "requires_motion_safety_parameters", nullable = false)
    private boolean requiresMotionSafetyParameters;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected AtomicCapabilityEntity() {
    }

    public AtomicCapabilityEntity(String id, String capabilityKey, String contractHash,
                                  String inputSchemaJson, String outputSchemaJson, String resourcesJson,
                                  CapabilitySideEffect sideEffect, CapabilityRetrySafety retrySafety,
                                  boolean safetyCritical, boolean requiresMotionSafetyParameters, Instant syncedAt) {
        this.id = id;
        this.capabilityKey = capabilityKey;
        this.contractHash = contractHash;
        this.inputSchemaJson = inputSchemaJson;
        this.outputSchemaJson = outputSchemaJson;
        this.resourcesJson = resourcesJson;
        this.sideEffect = sideEffect;
        this.retrySafety = retrySafety;
        this.safetyCritical = safetyCritical;
        this.requiresMotionSafetyParameters = requiresMotionSafetyParameters;
        this.active = true;
        this.syncedAt = syncedAt;
    }

    public String getId() { return id; }
    public String getCapabilityKey() { return capabilityKey; }
    public String getContractHash() { return contractHash; }
    public String getInputSchemaJson() { return inputSchemaJson; }
    public String getOutputSchemaJson() { return outputSchemaJson; }
    public String getResourcesJson() { return resourcesJson; }
    public CapabilitySideEffect getSideEffect() { return sideEffect; }
    public CapabilityRetrySafety getRetrySafety() { return retrySafety; }
    public boolean isSafetyCritical() { return safetyCritical; }
    public boolean isRequiresMotionSafetyParameters() { return requiresMotionSafetyParameters; }
    public boolean isActive() { return active; }
    public Instant getSyncedAt() { return syncedAt; }

    /** 契约未变化时只刷新同步时间，保持目录写入稳定。 */
    public void markSynchronized(Instant synchronizedAt) {
        this.active = true;
        this.syncedAt = synchronizedAt;
    }

    /** 上游只暴露当前目录；契约变化由下游记录新 Hash，并使旧发布计划失配。 */
    public void updateContract(String contractHash, String inputSchemaJson, String outputSchemaJson,
                               String resourcesJson, CapabilitySideEffect sideEffect,
                               CapabilityRetrySafety retrySafety, boolean safetyCritical,
                               boolean requiresMotionSafetyParameters, Instant synchronizedAt) {
        this.contractHash = contractHash;
        this.inputSchemaJson = inputSchemaJson;
        this.outputSchemaJson = outputSchemaJson;
        this.resourcesJson = resourcesJson;
        this.sideEffect = sideEffect;
        this.retrySafety = retrySafety;
        this.safetyCritical = safetyCritical;
        this.requiresMotionSafetyParameters = requiresMotionSafetyParameters;
        markSynchronized(synchronizedAt);
    }
}
