package com.kunling.scheduling.action.commissioning.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.commissioning.application.ActionParameterSetView;
import com.kunling.scheduling.action.commissioning.application.SaveParameterSetRequest;
import com.kunling.scheduling.action.shared.JsonCodec;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "action_parameter_set")
public class ActionParameterSetEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "action_key", length = 128, nullable = false)
    private String actionKey;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "robot_id", length = 128)
    private String robotId;

    @Column(name = "fixture_key", length = 128)
    private String fixtureKey;

    @Column(name = "material_key", length = 128)
    private String materialKey;

    @Lob
    @Column(name = "values_json", nullable = false, columnDefinition = "longtext")
    private String valuesJson;

    @Column(name = "revision", nullable = false)
    private long revision;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ActionParameterSetEntity() {
    }

    public ActionParameterSetEntity(String id, SaveParameterSetRequest request, JsonCodec jsonCodec, Instant now) {
        this.id = id;
        this.actionKey = request.actionKey();
        apply(request, jsonCodec);
        this.revision = 1L;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(SaveParameterSetRequest request, JsonCodec jsonCodec, Instant now) {
        this.actionKey = request.actionKey();
        apply(request, jsonCodec);
        this.revision++;
        this.updatedAt = now;
    }

    private void apply(SaveParameterSetRequest request, JsonCodec jsonCodec) {
        this.name = request.name();
        this.robotId = request.robotId();
        this.fixtureKey = request.fixtureKey();
        this.materialKey = request.materialKey();
        this.valuesJson = jsonCodec.write(request.values());
        this.enabled = request.enabled();
    }

    public ActionParameterSetView toView(JsonCodec jsonCodec, String activeExecutionId) {
        JsonNode values = jsonCodec.readTree(valuesJson);
        return new ActionParameterSetView(id, actionKey, name, robotId, fixtureKey, materialKey,
                values, revision, enabled, activeExecutionId != null, activeExecutionId,
                createdAt, updatedAt);
    }

    public String getId() { return id; }
    public String getActionKey() { return actionKey; }
    public long getRevision() { return revision; }
    public boolean isEnabled() { return enabled; }
}
