package com.kunling.scheduling.action.interfaces.rest;

import com.kunling.scheduling.action.definition.domain.ActionDefinition;

import java.util.UUID;

public final class ActionRequests {

    private ActionRequests() {
    }

    public record SaveDraftRequest(ActionDefinition definition, UUID draftId, Long expectedRevision) {
    }

    public record CloneReleaseRequest(String actionKey, String sourceVersion, String newVersion) {
    }

    public record PublishDraftRequest(String changeSummary) {
    }
}
