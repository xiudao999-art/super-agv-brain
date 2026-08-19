package com.kunling.scheduling.action.definition.application;

import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionDraftStatus;

import java.time.Instant;
import java.util.UUID;

public record ActionDraftView(
        UUID id,
        String actionKey,
        long revision,
        ActionDefinition definition,
        ActionDraftStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
