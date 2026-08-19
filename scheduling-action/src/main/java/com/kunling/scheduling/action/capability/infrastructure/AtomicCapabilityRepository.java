package com.kunling.scheduling.action.capability.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AtomicCapabilityRepository extends JpaRepository<AtomicCapabilityEntity, String> {

    List<AtomicCapabilityEntity> findAllByActiveTrueOrderByCapabilityKeyAsc();

    Optional<AtomicCapabilityEntity> findByCapabilityKeyAndActiveTrue(String capabilityKey);

    Optional<AtomicCapabilityEntity> findByCapabilityKey(String capabilityKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AtomicCapabilityEntity capability set capability.active = false where capability.active = true")
    int deactivateAll();
}
