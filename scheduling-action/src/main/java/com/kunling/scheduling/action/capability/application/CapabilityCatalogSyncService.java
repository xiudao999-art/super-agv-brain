package com.kunling.scheduling.action.capability.application;

import com.kunling.scheduling.action.shared.ImmutableCollections;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.shared.JsonCodec;
import com.kunling.scheduling.action.upstream.application.AtomicCapabilityDescriptor;
import com.kunling.scheduling.action.upstream.application.UpstreamCapabilitySource;
import com.kunling.scheduling.action.capability.domain.CapabilityManifest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

@Service
public class CapabilityCatalogSyncService {

    private final UpstreamCapabilitySource source;
    private final CapabilityCatalogStore store;
    private final JsonCodec jsonCodec;
    private final Clock clock = Clock.systemUTC();

    public CapabilityCatalogSyncService(
            UpstreamCapabilitySource source,
            CapabilityCatalogStore store,
            JsonCodec jsonCodec) {
        this.source = source;
        this.store = store;
        this.jsonCodec = jsonCodec;
    }

    public CapabilitySyncResult synchronize() {
        java.util.List<com.kunling.scheduling.action.upstream.application.AtomicCapabilityDescriptor> capabilities =
                source.fetchCapabilities();
        if (capabilities == null || capabilities.isEmpty()) {
            // 空目录通常意味着上游故障或协议错误，不能据此把本地全部能力停用。
            throw new IllegalArgumentException("上游返回空的原子能力目录，已保留上一次有效快照。");
        }
        HashSet<String> keys = new HashSet<String>();
        capabilities.forEach(capability -> validate(capability, keys));
        List<CapabilityManifest> manifests = capabilities.stream()
                .map(this::toManifest)
                .collect(ImmutableCollections.toImmutableList());
        Instant syncedAt = clock.instant();
        CapabilityCatalogStore.CatalogStoreResult stored = store.upsert(manifests, syncedAt);
        return new CapabilitySyncResult(capabilities.size(), stored.created(), stored.updated(),
                stored.unchanged(), syncedAt);
    }

    private void validate(AtomicCapabilityDescriptor capability, HashSet<String> keys) {
        if (capability.capabilityKey() == null || capability.capabilityKey().trim().isEmpty()) {
            throw new IllegalArgumentException("上游能力目录包含缺少 capabilityKey 的记录。");
        }
        if (!keys.add(capability.capabilityKey())) {
            throw new IllegalArgumentException("上游能力目录包含重复 capabilityKey：" + capability.capabilityKey());
        }
    }

    private CapabilityManifest toManifest(AtomicCapabilityDescriptor capability) {
        String contractHash = jsonCodec.sha256(jsonCodec.writeCanonical(new ContractSnapshot(
                capability.inputSchema(), capability.outputSchema(), capability.resources(), capability.sideEffect(),
                capability.retrySafety(), capability.safetyCritical(), capability.requiresMotionSafetyParameters())));
        return capability.toManifest(contractHash);
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class ContractSnapshot {
        Object inputSchema;
        Object outputSchema;
        Object resources;
        Object sideEffect;
        Object retrySafety;
        boolean safetyCritical;
        boolean requiresMotionSafetyParameters;
        @ConstructorProperties({"inputSchema", "outputSchema", "resources", "sideEffect", "retrySafety", "safetyCritical", "requiresMotionSafetyParameters"})
        public ContractSnapshot(
                Object inputSchema,
                Object outputSchema,
                Object resources,
                Object sideEffect,
                Object retrySafety,
                boolean safetyCritical,
                boolean requiresMotionSafetyParameters
        ) {
            this.inputSchema = inputSchema;
            this.outputSchema = outputSchema;
            this.resources = resources;
            this.sideEffect = sideEffect;
            this.retrySafety = retrySafety;
            this.safetyCritical = safetyCritical;
            this.requiresMotionSafetyParameters = requiresMotionSafetyParameters;
        }

    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class CapabilitySyncResult {
        int received;
        int created;
        int updated;
        int unchanged;
        Instant syncedAt;
        @ConstructorProperties({"received", "created", "updated", "unchanged", "syncedAt"})
        public CapabilitySyncResult(
                int received,
                int created,
                int updated,
                int unchanged,
                Instant syncedAt
        ) {
            this.received = received;
            this.created = created;
            this.updated = updated;
            this.unchanged = unchanged;
            this.syncedAt = syncedAt;
        }

    }
}
