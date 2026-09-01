package com.kunling.scheduling.app.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.app.domain.entity.LocationType;
import com.kunling.scheduling.app.service.LocationTypeService;
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
@RequestMapping("/locationTypes")
@Tag(name = "库位类型管理", description = "维护库位类型编码及状态")
public class LocationTypeController extends BaseController {
    private final LocationTypeService service;

    public LocationTypeController(LocationTypeService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "查询库位类型列表")
    public ApiResult<List<LocationType>> list(
            @Parameter(description = "库位类型编码") @RequestParam(required = false) String typeCode,
            @Parameter(description = "库位类型状态") @RequestParam(required = false) Integer status) {
        return success(service.list(Wrappers.<LocationType>lambdaQuery()
                .eq(hasText(typeCode), LocationType::getTypeCode, typeCode)
                .eq(status != null, LocationType::getStatus, status).orderByAsc(LocationType::getId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询库位类型详情")
    public ApiResult<LocationType> get(@PathVariable Long id) {
        return success(required(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增库位类型")
    public ApiResult<LocationType> create(@RequestBody LocationType entity) {
        entity.setId(null);
        service.save(entity);
        return created(required(entity.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改库位类型")
    public ApiResult<LocationType> update(@PathVariable Long id, @RequestBody LocationType entity) {
        required(id);
        entity.setId(id);
        service.updateById(entity);
        return success(required(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除库位类型")
    public ApiResult<Void> delete(@PathVariable Long id) {
        required(id);
        service.removeById(id);
        return success();
    }

    private LocationType required(Long id) {
        LocationType entity = service.getById(id);
        if (entity == null) throw new ResourceNotFoundException("库位类型不存在: " + id);
        return entity;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
