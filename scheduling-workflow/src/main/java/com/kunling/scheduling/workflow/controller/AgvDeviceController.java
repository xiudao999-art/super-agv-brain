package com.kunling.scheduling.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kunling.scheduling.common.exception.ResourceNotFoundException;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import com.kunling.scheduling.workflow.entity.AgvDevice;
import com.kunling.scheduling.workflow.service.AgvDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agv-devices")
@Tag(name = "AGV设备管理", description = "AGV设备基础增删改查")

public class AgvDeviceController extends BaseController {

    private final AgvDeviceService agvDeviceService;

    public AgvDeviceController(AgvDeviceService agvDeviceService) {
        this.agvDeviceService = agvDeviceService;
    }

    @GetMapping
    @Operation(summary = "分页查询AGV设备")
    public ApiResult<IPage<AgvDevice>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return success(agvDeviceService.page(new Page<>(pageNum, pageSize)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询AGV设备详情")
    public ApiResult<AgvDevice> get(@PathVariable Long id) {
        return success(required(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增AGV设备")
    public ApiResult<AgvDevice> create(@RequestBody AgvDevice entity) {
        entity.setId(null);
        agvDeviceService.save(entity);
        return created(entity);
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改AGV设备")
    public ApiResult<AgvDevice> update(@PathVariable Long id, @RequestBody AgvDevice entity) {
        required(id);
        entity.setId(id);
        agvDeviceService.updateById(entity);
        return success(required(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除AGV设备")
    public ApiResult<Void> delete(@PathVariable Long id) {
        required(id);
        agvDeviceService.removeById(id);
        return success();
    }

    private AgvDevice required(Long id) {
        AgvDevice entity = agvDeviceService.getById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("AGV设备不存在: " + id);
        }
        return entity;
    }
}
