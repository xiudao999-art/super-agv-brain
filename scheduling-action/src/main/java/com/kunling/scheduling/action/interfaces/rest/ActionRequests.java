package com.kunling.scheduling.action.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.definition.domain.ActionDefinition;

import java.util.UUID;

public final class ActionRequests {

    private ActionRequests() {
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class SaveDraftRequest {
        ActionDefinition definition;
        UUID draftId;
        Long expectedRevision;
        @ConstructorProperties({"definition", "draftId", "expectedRevision"})
        public SaveDraftRequest(
                ActionDefinition definition,
                UUID draftId,
                Long expectedRevision
        ) {
            this.definition = definition;
            this.draftId = draftId;
            this.expectedRevision = expectedRevision;
        }

    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class CloneReleaseRequest {
        String actionKey;
        String sourceVersion;
        String newVersion;
        @ConstructorProperties({"actionKey", "sourceVersion", "newVersion"})
        public CloneReleaseRequest(
                String actionKey,
                String sourceVersion,
                String newVersion
        ) {
            this.actionKey = actionKey;
            this.sourceVersion = sourceVersion;
            this.newVersion = newVersion;
        }

    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class PublishDraftRequest {
        String changeSummary;
        @ConstructorProperties({"changeSummary"})
        public PublishDraftRequest(
                String changeSummary
        ) {
            this.changeSummary = changeSummary;
        }

    }
}
