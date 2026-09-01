package com.kunling.scheduling.app.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.app.domain.entity.Location;
import com.kunling.scheduling.app.service.LocationService;
import com.kunling.scheduling.common.exception.ResourceNotFoundException;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/locations")
@Tag(name = "库位管理", description = "维护库位编码、类型及启用状态")
public class LocationController extends BaseController {
    private final LocationService service;
    public LocationController(LocationService service) { this.service = service; }

    @GetMapping @Operation(summary = "查询库位列表")
    public ApiResult<List<Location>> list(
            @Parameter(description = "库位编码") @RequestParam(required = false) String locationCode,
            @Parameter(description = "库位类型") @RequestParam(required = false) String locationType,
            @Parameter(description = "启用标记：1 启用，0 停用", example = "1")
            @RequestParam(required = false) Integer enabled) {
        return success(service.list(Wrappers.<Location>lambdaQuery()
            .eq(hasText(locationCode), Location::getLocationCode, locationCode)
            .eq(hasText(locationType), Location::getLocationType, locationType)
            .eq(enabled != null, Location::getEnabled, enabled).orderByAsc(Location::getId)));
    }
    @GetMapping("/{id}") @Operation(summary = "查询库位详情")
    public ApiResult<Location> get(@PathVariable Long id) { return success(required(id)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "新增库位")
    public ApiResult<Location> create(@RequestBody Location entity) {
        entity.setId(null); service.save(entity);
        return created(required(entity.getId()));
    }
    @PutMapping("/{id}") @Operation(summary = "修改库位")
    public ApiResult<Location> update(@PathVariable Long id, @RequestBody Location entity) {
        required(id); entity.setId(id); service.updateById(entity); return success(required(id));
    }
    @DeleteMapping("/{id}") @Operation(summary = "删除库位")
    public ApiResult<Void> delete(@PathVariable Long id) {
        required(id); service.removeById(id); return success();
    }
    private Location required(Long id) {
        Location entity = service.getById(id);
        if (entity == null) throw new ResourceNotFoundException("库位不存在: " + id);
        return entity;
    }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}
