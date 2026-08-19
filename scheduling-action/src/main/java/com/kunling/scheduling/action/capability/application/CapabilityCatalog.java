package com.kunling.scheduling.action.capability.application;

import com.kunling.scheduling.action.capability.domain.CapabilityManifest;

import java.util.List;
import java.util.Optional;

public interface CapabilityCatalog {

    List<CapabilityManifest> listAll();

    Optional<CapabilityManifest> find(String capabilityKey);
}
