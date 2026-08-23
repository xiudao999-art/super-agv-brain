package com.kunling.scheduling.agvflow.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.agvflow.domain.entity.Location;
import com.kunling.scheduling.agvflow.service.LocationService;
import com.kunling.scheduling.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/locations")
@Tag(name = "库位管理", description = "维护库位编码、类型及启用状态")
public class LocationController {
    private final LocationService service;
    public LocationController(LocationService service) { this.service = service; }

    @GetMapping @Operation(summary = "查询库位列表")
    public List<Location> list(
            @Parameter(description = "库位编码") @RequestParam(required = false) String locationCode,
            @Parameter(description = "库位类型") @RequestParam(required = false) String locationType,
            @Parameter(description = "启用标记：1 启用，0 停用", example = "1")
            @RequestParam(required = false) Integer enabled) {
        return service.list(Wrappers.<Location>lambdaQuery()
            .eq(hasText(locationCode), Location::getLocationCode, locationCode)
            .eq(hasText(locationType), Location::getLocationType, locationType)
            .eq(enabled != null, Location::getEnabled, enabled).orderByAsc(Location::getId));
    }
    @GetMapping("/{id}") @Operation(summary = "查询库位详情")
    public Location get(@PathVariable Long id) { return required(id); }
    @PostMapping @Operation(summary = "新增库位")
    public ResponseEntity<Location> create(@RequestBody Location entity) {
        entity.setId(null); service.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(required(entity.getId()));
    }
    @PutMapping("/{id}") @Operation(summary = "修改库位")
    public Location update(@PathVariable Long id, @RequestBody Location entity) {
        required(id); entity.setId(id); service.updateById(entity); return required(id);
    }
    @DeleteMapping("/{id}") @Operation(summary = "删除库位")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        required(id); service.removeById(id); return ResponseEntity.noContent().build();
    }
    private Location required(Long id) {
        Location entity = service.getById(id);
        if (entity == null) throw new ResourceNotFoundException("库位不存在: " + id);
        return entity;
    }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}
