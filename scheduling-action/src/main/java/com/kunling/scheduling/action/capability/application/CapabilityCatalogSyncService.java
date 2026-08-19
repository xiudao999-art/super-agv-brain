package com.kunling.scheduling.action.capability.application;

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
        var capabilities = source.fetchCapabilities();
        if (capabilities == null || capabilities.isEmpty()) {
            // 空目录通常意味着上游故障或协议错误，不能据此把本地全部能力停用。
            throw new IllegalArgumentException("上游返回空的原子能力目录，已保留上一次有效快照。");
        }
        var keys = new HashSet<String>();
        capabilities.forEach(capability -> validate(capability, keys));
        List<CapabilityManifest> manifests = capabilities.stream()
                .map(this::toManifest)
                .toList();
        Instant syncedAt = clock.instant();
        var stored = store.upsert(manifests, syncedAt);
        return new CapabilitySyncResult(capabilities.size(), stored.created(), stored.updated(),
                stored.unchanged(), syncedAt);
    }

    private void validate(AtomicCapabilityDescriptor capability, HashSet<String> keys) {
        if (capability.capabilityKey() == null || capability.capabilityKey().isBlank()) {
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

    private record ContractSnapshot(Object inputSchema, Object outputSchema, Object resources,
                                    Object sideEffect, Object retrySafety, boolean safetyCritical,
                                    boolean requiresMotionSafetyParameters) {
    }

    public record CapabilitySyncResult(int received, int created, int updated, int unchanged, Instant syncedAt) {
    }
}
