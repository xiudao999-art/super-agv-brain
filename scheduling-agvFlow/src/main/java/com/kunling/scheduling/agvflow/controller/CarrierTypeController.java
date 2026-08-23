package com.kunling.scheduling.agvflow.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.agvflow.domain.entity.CarrierType;
import com.kunling.scheduling.agvflow.service.CarrierTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/carrierTypes")
@Tag(name = "载具类型管理", description = "维护载具类型及其可用状态")
public class CarrierTypeController {
    private final CarrierTypeService service;

    public CarrierTypeController(CarrierTypeService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "查询载具类型列表")
    public List<CarrierType> list(
            @Parameter(description = "载具类型编码") @RequestParam(required = false) String typeCode,
            @Parameter(description = "载具类型状态") @RequestParam(required = false) String status) {
        return service.list(Wrappers.<CarrierType>lambdaQuery()
                .eq(hasText(typeCode), CarrierType::getTypeCode, typeCode)
                .eq(hasText(status), CarrierType::getStatus, status).orderByAsc(CarrierType::getId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询载具类型详情")
    public CarrierType get(@PathVariable Long id) {
        return required(id);
    }

    @PostMapping
    @Operation(summary = "新增载具类型")
    public ResponseEntity<CarrierType> create(@RequestBody CarrierType entity) {
        entity.setId(null);
        service.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(required(entity.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改载具类型")
        public CarrierType update (@PathVariable Long id, @RequestBody CarrierType entity){
            required(id);
            entity.setId(id);
            service.updateById(entity);
            return required(id);
        }

        @DeleteMapping("/{id}")
        @Operation(summary = "删除载具类型")
        public ResponseEntity<Void> delete (@PathVariable Long id){
            required(id);
            service.removeById(id);
            return ResponseEntity.noContent().build();
        }

        private CarrierType required (Long id){
            CarrierType entity = service.getById(id);
            if (entity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "载具类型不存在: " + id);
            return entity;
        }

        private boolean hasText (String value){
            return value != null && !value.trim().isEmpty();
        }
    }
