package com.kunling.scheduling.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kunling.scheduling.common.exception.ResourceNotFoundException;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import com.kunling.scheduling.workflow.entity.HardwareInfo;
import com.kunling.scheduling.workflow.service.HardwareInfoService;
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
@RequestMapping("/api/hardware-info")
@Tag(name = "硬件信息管理", description = "硬件信息基础增删改查")

public class HardwareInfoController extends BaseController {

    private final HardwareInfoService hardwareInfoService;

    public HardwareInfoController(HardwareInfoService hardwareInfoService) {
        this.hardwareInfoService = hardwareInfoService;
    }

    @GetMapping
    @Operation(summary = "分页查询硬件信息")
    public ApiResult<IPage<HardwareInfo>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return success(hardwareInfoService.page(new Page<>(pageNum, pageSize)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询硬件信息详情")
    public ApiResult<HardwareInfo> get(@PathVariable Long id) {
        return success(required(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增硬件信息")
    public ApiResult<HardwareInfo> create(@RequestBody HardwareInfo entity) {
        entity.setId(null);
        hardwareInfoService.save(entity);
        return created(entity);
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改硬件信息")
    public ApiResult<HardwareInfo> update(@PathVariable Long id, @RequestBody HardwareInfo entity) {
        required(id);
        entity.setId(id);
        hardwareInfoService.updateById(entity);
        return success(required(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除硬件信息")
    public ApiResult<Void> delete(@PathVariable Long id) {
        required(id);
        hardwareInfoService.removeById(id);
        return success();
    }

    private HardwareInfo required(Long id) {
        HardwareInfo entity = hardwareInfoService.getById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("硬件信息不存在: " + id);
        }
        return entity;
    }
}
