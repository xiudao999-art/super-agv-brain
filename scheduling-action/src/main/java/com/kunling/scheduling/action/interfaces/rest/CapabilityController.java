package com.kunling.scheduling.action.interfaces.rest;

import com.kunling.scheduling.action.capability.application.CapabilityCatalog;
import com.kunling.scheduling.action.capability.application.CapabilityCatalogSyncService;
import com.kunling.scheduling.action.capability.domain.CapabilityManifest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.kunling.scheduling.action.interfaces.docs.ActionApiDocumentation.TAG_CAPABILITY;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = TAG_CAPABILITY, description = "查询并同步上游原子能力目录")
public class CapabilityController {

    private final CapabilityCatalog capabilityCatalog;
    private final CapabilityCatalogSyncService syncService;

    public CapabilityController(CapabilityCatalog capabilityCatalog, CapabilityCatalogSyncService syncService) {
        this.capabilityCatalog = capabilityCatalog;
        this.syncService = syncService;
    }

    @GetMapping("/capabilities")
    @Operation(summary = "查询原子能力目录", description = "返回下游当前保存的全部原子能力契约")
    @ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true)
    public List<CapabilityManifest> capabilities() {
        return capabilityCatalog.listAll();
    }

    @PostMapping("/capabilities/synchronize")
    @Operation(summary = "同步原子能力目录", description = "主动从上游拉取最新原子能力契约并更新本地目录")
    @ApiResponse(responseCode = "200", description = "同步完成", useReturnTypeSchema = true)
    public CapabilityCatalogSyncService.CapabilitySyncResult synchronize() {
        return syncService.synchronize();
    }
}
