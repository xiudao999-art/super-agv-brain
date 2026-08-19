package com.kunling.scheduling.action.definition.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionDraftStatus;

import java.time.Instant;
import java.util.UUID;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionDraftView {
    UUID id;
    String actionKey;
    long revision;
    ActionDefinition definition;
    ActionDraftStatus status;
    Instant createdAt;
    Instant updatedAt;
    @ConstructorProperties({"id", "actionKey", "revision", "definition", "status", "createdAt", "updatedAt"})
    public ActionDraftView(
            UUID id,
            String actionKey,
            long revision,
            ActionDefinition definition,
            ActionDraftStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.actionKey = actionKey;
        this.revision = revision;
        this.definition = definition;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

}
