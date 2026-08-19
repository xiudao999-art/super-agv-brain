package com.kunling.scheduling.action.interfaces.rest;

import com.kunling.scheduling.action.capability.application.CapabilityCatalog;
import com.kunling.scheduling.action.capability.application.CapabilityCatalogSyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CapabilityController {

    private final CapabilityCatalog capabilityCatalog;
    private final CapabilityCatalogSyncService syncService;

    public CapabilityController(CapabilityCatalog capabilityCatalog, CapabilityCatalogSyncService syncService) {
        this.capabilityCatalog = capabilityCatalog;
        this.syncService = syncService;
    }

    @GetMapping("/capabilities")
    public Object capabilities() {
        return capabilityCatalog.listAll();
    }

    @PostMapping("/capabilities/synchronize")
    public Object synchronize() {
        return syncService.synchronize();
    }
}
