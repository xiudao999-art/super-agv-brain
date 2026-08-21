package com.kunling.scheduling.action.definition.infrastructure;

import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionDefinitionStatus;
import com.kunling.scheduling.action.config.JsonCodec;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.time.Instant;

/** 当前 Action 配置表；每个 actionKey 永远只有一行。 */
@Entity
@Table(name = "action_definition")
public class ActionDefinitionEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "action_key", length = 128, nullable = false, unique = true)
    private String actionKey;

    @Column(name = "downstream_action_type", length = 64, nullable = false)
    private String downstreamActionType;

    @Column(name = "revision", nullable = false)
    private long revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private ActionDefinitionStatus status;

    @Lob
    @Column(name = "definition_json", nullable = false, columnDefinition = "longtext")
    private String definitionJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ActionDefinitionEntity() {
    }

    public ActionDefinitionEntity(String id, ActionDefinition definition, JsonCodec jsonCodec, Instant now) {
        this.id = id;
        this.actionKey = definition.actionKey();
        this.downstreamActionType = definition.downstreamActionType().wireName();
        this.revision = 1L;
        this.status = ActionDefinitionStatus.DRAFT;
        this.definitionJson = jsonCodec.write(definition);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(ActionDefinition definition, JsonCodec jsonCodec, Instant now) {
        this.downstreamActionType = definition.downstreamActionType().wireName();
        this.definitionJson = jsonCodec.write(definition);
        this.status = ActionDefinitionStatus.DRAFT;
        this.revision++;
        this.updatedAt = now;
    }

    public void changeStatus(ActionDefinitionStatus target, Instant now) {
        this.status = target;
        this.revision++;
        this.updatedAt = now;
    }

    public ActionDefinition definition(JsonCodec jsonCodec) {
        return jsonCodec.read(definitionJson, ActionDefinition.class);
    }

    public String getId() { return id; }
    public String getActionKey() { return actionKey; }
    public long getRevision() { return revision; }
    public ActionDefinitionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
