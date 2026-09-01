package com.kunling.scheduling.app.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.app.domain.entity.CarrierType;
import com.kunling.scheduling.app.service.CarrierTypeService;
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
@RequestMapping("/carrierTypes")
@Tag(name = "载具类型管理", description = "维护载具类型及其可用状态")
public class CarrierTypeController extends BaseController {
    private final CarrierTypeService service;

    public CarrierTypeController(CarrierTypeService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "查询载具类型列表")
    public ApiResult<List<CarrierType>> list(
            @Parameter(description = "载具类型编码") @RequestParam(required = false) String typeCode,
            @Parameter(description = "载具类型状态") @RequestParam(required = false) String status) {
        return success(service.list(Wrappers.<CarrierType>lambdaQuery()
                .eq(hasText(typeCode), CarrierType::getTypeCode, typeCode)
                .eq(hasText(status), CarrierType::getStatus, status).orderByAsc(CarrierType::getId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询载具类型详情")
    public ApiResult<CarrierType> get(@PathVariable Long id) {
        return success(required(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增载具类型")
    public ApiResult<CarrierType> create(@RequestBody CarrierType entity) {
        entity.setId(null);
        service.save(entity);
        return created(required(entity.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改载具类型")
    public ApiResult<CarrierType> update(@PathVariable Long id, @RequestBody CarrierType entity) {
        required(id);
        entity.setId(id);
        service.updateById(entity);
        return success(required(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除载具类型")
    public ApiResult<Void> delete(@PathVariable Long id) {
        required(id);
        service.removeById(id);
        return success();
    }

    private CarrierType required(Long id) {
        CarrierType entity = service.getById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("载具类型不存在: " + id);
        }
        return entity;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
