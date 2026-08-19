package com.kunling.scheduling.action.capability.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.capability.domain.CapabilityManifest;

import java.time.Instant;
import java.util.List;

public interface CapabilityCatalogStore {

    CatalogStoreResult upsert(List<CapabilityManifest> capabilities, Instant syncedAt);

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class CatalogStoreResult {
        int created;
        int updated;
        int unchanged;
        @ConstructorProperties({"created", "updated", "unchanged"})
        public CatalogStoreResult(
                int created,
                int updated,
                int unchanged
        ) {
            this.created = created;
            this.updated = updated;
            this.unchanged = unchanged;
        }

    }
}
