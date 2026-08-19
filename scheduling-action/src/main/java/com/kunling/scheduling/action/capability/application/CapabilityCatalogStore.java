package com.kunling.scheduling.action.capability.application;

import com.kunling.scheduling.action.capability.domain.CapabilityManifest;

import java.time.Instant;
import java.util.List;

public interface CapabilityCatalogStore {

    CatalogStoreResult upsert(List<CapabilityManifest> capabilities, Instant syncedAt);

    record CatalogStoreResult(int created, int updated, int unchanged) {
    }
}
