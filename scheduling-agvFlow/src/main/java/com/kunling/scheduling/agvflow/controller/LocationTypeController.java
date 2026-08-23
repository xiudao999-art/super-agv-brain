package com.kunling.scheduling.agvflow.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.agvflow.domain.entity.LocationType;
import com.kunling.scheduling.agvflow.service.LocationTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/locationTypes")
@Tag(name = "库位类型管理", description = "维护库位类型编码及状态")
public class LocationTypeController {
    private final LocationTypeService service;

    public LocationTypeController(LocationTypeService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "查询库位类型列表")
    public List<LocationType> list(
            @Parameter(description = "库位类型编码") @RequestParam(required = false) String typeCode,
            @Parameter(description = "库位类型状态") @RequestParam(required = false) Integer status) {
        return service.list(Wrappers.<LocationType>lambdaQuery()
                .eq(hasText(typeCode), LocationType::getTypeCode, typeCode)
                .eq(status != null, LocationType::getStatus, status).orderByAsc(LocationType::getId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询库位类型详情")
    public LocationType get(@PathVariable Long id) {
        return required(id);
    }

    @PostMapping
    @Operation(summary = "新增库位类型")
    public ResponseEntity<LocationType> create(@RequestBody LocationType entity) {
        entity.setId(null);
        service.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(required(entity.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改库位类型")
    public LocationType update(@PathVariable Long id, @RequestBody LocationType entity) {
        required(id);
        entity.setId(id);
        service.updateById(entity);
        return required(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除库位类型")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        required(id);
        service.removeById(id);
        return ResponseEntity.noContent().build();
    }

    private LocationType required(Long id) {
        LocationType entity = service.getById(id);
        if (entity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "库位类型不存在: " + id);
        return entity;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
