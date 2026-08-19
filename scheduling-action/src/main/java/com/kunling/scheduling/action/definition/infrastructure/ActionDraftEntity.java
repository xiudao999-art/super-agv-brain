package com.kunling.scheduling.action.definition.infrastructure;

import com.kunling.scheduling.action.definition.domain.ActionDraftStatus;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "action_draft")
public class ActionDraftEntity {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "action_key", length = 128, nullable = false)
    private String actionKey;

    @Column(name = "action_version", length = 32, nullable = false)
    private String actionVersion;

    @Column(nullable = false)
    private long revision;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private ActionDraftStatus status;

    @Lob
    @Column(name = "definition_json", nullable = false, columnDefinition = "longtext")
    private String definitionJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ActionDraftEntity() {
    }

    public ActionDraftEntity(String id, String actionKey, String actionVersion, long revision,
                             ActionDraftStatus status, String definitionJson, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.actionKey = actionKey;
        this.actionVersion = actionVersion;
        this.revision = revision;
        this.status = status;
        this.definitionJson = definitionJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getActionKey() { return actionKey; }
    public String getActionVersion() { return actionVersion; }
    public long getRevision() { return revision; }
    public ActionDraftStatus getStatus() { return status; }
    public String getDefinitionJson() { return definitionJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String actionKey, String actionVersion, String definitionJson, Instant now) {
        this.actionKey = actionKey;
        this.actionVersion = actionVersion;
        this.definitionJson = definitionJson;
        this.revision++;
        this.updatedAt = now;
    }

    public void markPublished(Instant now) {
        this.status = ActionDraftStatus.PUBLISHED;
        this.revision++;
        this.updatedAt = now;
    }
}
