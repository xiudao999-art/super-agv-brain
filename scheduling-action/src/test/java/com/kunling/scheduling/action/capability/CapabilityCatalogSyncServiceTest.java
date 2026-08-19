package com.kunling.scheduling.action.capability;

import com.kunling.scheduling.action.shared.ImmutableCollections;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kunling.scheduling.action.capability.application.CapabilityCatalogStore;
import com.kunling.scheduling.action.capability.application.CapabilityCatalogSyncService;
import com.kunling.scheduling.action.capability.domain.CapabilityManifest;
import com.kunling.scheduling.action.capability.domain.CapabilityRetrySafety;
import com.kunling.scheduling.action.capability.domain.CapabilitySideEffect;
import com.kunling.scheduling.action.definition.domain.ParameterSchema;
import com.kunling.scheduling.action.definition.domain.ParameterType;
import com.kunling.scheduling.action.shared.JsonCodec;
import com.kunling.scheduling.action.upstream.application.AtomicCapabilityDescriptor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityCatalogSyncServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final JsonCodec jsonCodec = new JsonCodec(objectMapper);

    @Test
    void calculatesTheCapabilityContractHashInsideTheDownstream() {
        Map<String, ParameterSchema> inputSchema = ImmutableCollections.mapOf("target",
                new ParameterSchema(ParameterType.STRING, true, null, ImmutableCollections.listOf(), ImmutableCollections.mapOf(), null));
        AtomicCapabilityDescriptor descriptor = descriptor(inputSchema);
        AtomicReference<List<CapabilityManifest>> stored = new AtomicReference<>();
        CapabilityCatalogStore store = (capabilities, syncedAt) -> {
            stored.set(capabilities);
            return new CapabilityCatalogStore.CatalogStoreResult(capabilities.size(), 0, 0);
        };
        CapabilityCatalogSyncService service = new CapabilityCatalogSyncService(
                () -> ImmutableCollections.listOf(descriptor), store, jsonCodec);

        CapabilityCatalogSyncService.CapabilitySyncResult result = service.synchronize();

        assertThat(result.received()).isOne();
        assertThat(stored.get()).singleElement().satisfies(manifest -> {
            assertThat(manifest.capabilityKey()).isEqualTo("test.move");
            assertThat(manifest.contractHash()).matches("[0-9a-f]{64}");
        });
    }

    @Test
    void rejectsDuplicateCapabilityKeysAndNeverCallsTheStore() {
        AtomicReference<List<CapabilityManifest>> stored = new AtomicReference<>();
        CapabilityCatalogStore store = (capabilities, syncedAt) -> {
            stored.set(capabilities);
            return new CapabilityCatalogStore.CatalogStoreResult(0, 0, 0);
        };
        AtomicCapabilityDescriptor descriptor = descriptor(ImmutableCollections.mapOf());
        CapabilityCatalogSyncService service = new CapabilityCatalogSyncService(
                () -> ImmutableCollections.listOf(descriptor, descriptor), store, jsonCodec);

        assertThatThrownBy(service::synchronize).hasMessageContaining("重复 capabilityKey");
        assertThat(stored.get()).isNull();
    }

    private AtomicCapabilityDescriptor descriptor(Map<String, ParameterSchema> inputSchema) {
        return new AtomicCapabilityDescriptor("test.move", inputSchema, ImmutableCollections.mapOf(),
                ImmutableCollections.listOf("arm"), CapabilitySideEffect.PHYSICAL,
                CapabilityRetrySafety.VERIFY_BEFORE_RETRY, true, false);
    }
}
