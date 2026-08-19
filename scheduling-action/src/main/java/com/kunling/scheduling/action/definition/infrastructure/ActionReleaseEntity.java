package com.kunling.scheduling.action.definition.infrastructure;

import com.kunling.scheduling.action.definition.domain.ActionReleaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "action_release")
public class ActionReleaseEntity {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "action_key", length = 128, nullable = false)
    private String actionKey;

    @Column(name = "action_version", length = 32, nullable = false)
    private String actionVersion;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private ActionReleaseStatus status;

    @Column(name = "compiler_version", length = 32, nullable = false)
    private String compilerVersion;

    @Lob
    @Column(name = "definition_json", nullable = false, columnDefinition = "longtext")
    private String definitionJson;

    @Lob
    @Column(name = "plan_json", nullable = false, columnDefinition = "longtext")
    private String planJson;

    @Lob
    @Column(name = "canonical_json", nullable = false, columnDefinition = "longtext")
    private String canonicalJson;

    @Column(name = "plan_hash", length = 64, nullable = false)
    private String planHash;

    @Column(name = "change_summary", length = 1000, nullable = false)
    private String changeSummary;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "deprecated_at")
    private Instant deprecatedAt;

    protected ActionReleaseEntity() {
    }

    public ActionReleaseEntity(String id, String actionKey, String actionVersion, ActionReleaseStatus status,
                               String compilerVersion, String definitionJson, String planJson,
                               String canonicalJson, String planHash, String changeSummary, Instant publishedAt) {
        this.id = id;
        this.actionKey = actionKey;
        this.actionVersion = actionVersion;
        this.status = status;
        this.compilerVersion = compilerVersion;
        this.definitionJson = definitionJson;
        this.planJson = planJson;
        this.canonicalJson = canonicalJson;
        this.planHash = planHash;
        this.changeSummary = changeSummary;
        this.publishedAt = publishedAt;
    }

    public String getId() { return id; }
    public String getActionKey() { return actionKey; }
    public String getActionVersion() { return actionVersion; }
    public ActionReleaseStatus getStatus() { return status; }
    public String getCompilerVersion() { return compilerVersion; }
    public String getDefinitionJson() { return definitionJson; }
    public String getPlanJson() { return planJson; }
    public String getCanonicalJson() { return canonicalJson; }
    public String getPlanHash() { return planHash; }
    public String getChangeSummary() { return changeSummary; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getDeprecatedAt() { return deprecatedAt; }

    public void deprecate(Instant now) {
        if (status == ActionReleaseStatus.PUBLISHED) {
            status = ActionReleaseStatus.DEPRECATED;
            deprecatedAt = now;
        }
    }
}
