package com.kunling.scheduling.agvflow.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.agvflow.domain.entity.Carrier;
import com.kunling.scheduling.agvflow.service.CarrierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/carriers")
@Tag(name = "载具管理", description = "维护载具基础信息、状态及启用标记")
public class CarrierController {
    private final CarrierService service;

    public CarrierController(CarrierService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "查询载具列表")
    public List<Carrier> list(
            @Parameter(description = "载具编码") @RequestParam(required = false) String carrierCode,
            @Parameter(description = "载具状态") @RequestParam(required = false) String carrierStatus,
            @Parameter(description = "启用标记：1 启用，0 停用", example = "1")
            @RequestParam(required = false) Integer enabled) {
        return service.list(Wrappers.<Carrier>lambdaQuery()
                .eq(hasText(carrierCode), Carrier::getCarrierCode, carrierCode)
                .eq(hasText(carrierStatus), Carrier::getCarrierStatus, carrierStatus)
                .eq(enabled != null, Carrier::getEnabled, enabled).orderByAsc(Carrier::getId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询载具详情")
    public Carrier get(@PathVariable Long id) {
        return required(id);
    }

    @PostMapping
    @Operation(summary = "新增载具")
    public ResponseEntity<Carrier> create(@RequestBody Carrier entity) {
        entity.setId(null);
        service.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(required(entity.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改载具")
    public Carrier update(@PathVariable Long id, @RequestBody Carrier entity) {
        required(id);
        entity.setId(id);
        service.updateById(entity);
        return required(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除载具")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        required(id);
        service.removeById(id);
        return ResponseEntity.noContent().build();
    }

    private Carrier required(Long id) {
        Carrier entity = service.getById(id);
        if (entity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "载具不存在: " + id);
        return entity;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
