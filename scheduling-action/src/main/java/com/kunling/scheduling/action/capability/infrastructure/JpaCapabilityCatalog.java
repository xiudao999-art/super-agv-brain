package com.kunling.scheduling.action.capability.infrastructure;

import com.kunling.scheduling.action.shared.ImmutableCollections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.action.capability.application.CapabilityCatalog;
import com.kunling.scheduling.action.capability.application.CapabilityCatalogStore;
import com.kunling.scheduling.action.capability.domain.CapabilityManifest;
import com.kunling.scheduling.action.definition.domain.ParameterSchema;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.kunling.scheduling.action.shared.JsonCodec;

@Repository
public class JpaCapabilityCatalog implements CapabilityCatalog, CapabilityCatalogStore {

    private static final TypeReference<Map<String, ParameterSchema>> SCHEMA_TYPE =
            new TypeReference<Map<String, ParameterSchema>>() { };
    private static final TypeReference<List<String>> STRING_LIST_TYPE =
            new TypeReference<List<String>>() { };

    private final AtomicCapabilityRepository repository;
    private final ObjectMapper objectMapper;
    private final JsonCodec jsonCodec;

    public JpaCapabilityCatalog(AtomicCapabilityRepository repository, ObjectMapper objectMapper, JsonCodec jsonCodec) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.jsonCodec = jsonCodec;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CapabilityManifest> listAll() {
        return repository.findAllByActiveTrueOrderByCapabilityKeyAsc().stream()
                .map(this::toManifest)
                .collect(ImmutableCollections.toImmutableList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CapabilityManifest> find(String capabilityKey) {
        return repository.findByCapabilityKeyAndActiveTrue(capabilityKey).map(this::toManifest);
    }

    @Override
    @Transactional
    public CatalogStoreResult upsert(List<CapabilityManifest> capabilities, Instant syncedAt) {
        repository.deactivateAll();
        int created = 0;
        int updated = 0;
        for (CapabilityManifest capability : capabilities) {
            AtomicCapabilityEntity existing = repository.findByCapabilityKey(capability.capabilityKey()).orElse(null);
            if (existing != null) {
                if (existing.getContractHash().equalsIgnoreCase(capability.contractHash())) {
                    existing.markSynchronized(syncedAt);
                } else {
                    existing.updateContract(capability.contractHash(), jsonCodec.write(capability.inputSchema()),
                            jsonCodec.write(capability.outputSchema()), jsonCodec.write(capability.resources()),
                            capability.sideEffect(), capability.retrySafety(), capability.safetyCritical(),
                            capability.requiresMotionSafetyParameters(), syncedAt);
                    updated++;
                }
                repository.save(existing);
                continue;
            }
            repository.save(new AtomicCapabilityEntity(UUID.randomUUID().toString(), capability.capabilityKey(),
                    capability.contractHash(), jsonCodec.write(capability.inputSchema()),
                    jsonCodec.write(capability.outputSchema()), jsonCodec.write(capability.resources()),
                    capability.sideEffect(), capability.retrySafety(), capability.safetyCritical(),
                    capability.requiresMotionSafetyParameters(), syncedAt));
            created++;
        }
        return new CatalogStoreResult(created, updated, capabilities.size() - created - updated);
    }

    private CapabilityManifest toManifest(AtomicCapabilityEntity entity) {
        try {
            return new CapabilityManifest(entity.getCapabilityKey(), entity.getContractHash(),
                    objectMapper.readValue(entity.getInputSchemaJson(), SCHEMA_TYPE),
                    objectMapper.readValue(entity.getOutputSchemaJson(), SCHEMA_TYPE),
                    objectMapper.readValue(entity.getResourcesJson(), STRING_LIST_TYPE),
                    entity.getSideEffect(), entity.getRetrySafety(), entity.isSafetyCritical(),
                    entity.isRequiresMotionSafetyParameters());
        } catch (Exception exception) {
            throw new IllegalStateException("原子能力目录 JSON 损坏：" + entity.getCapabilityKey(), exception);
        }
    }
}
