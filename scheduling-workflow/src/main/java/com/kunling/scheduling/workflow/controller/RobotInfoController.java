package com.kunling.scheduling.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kunling.scheduling.common.exception.ResourceNotFoundException;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import com.kunling.scheduling.workflow.dto.RobotInfoSaveDto;
import com.kunling.scheduling.workflow.entity.RobotInfo;
import com.kunling.scheduling.workflow.resp.RobotInfoResp;
import com.kunling.scheduling.workflow.service.RobotInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/robot-info")
@Tag(name = "机器人信息管理", description = "机器人信息基础增删改查")
public class RobotInfoController extends BaseController {

    private final RobotInfoService robotInfoService;

    public RobotInfoController(RobotInfoService robotInfoService) {
        this.robotInfoService = robotInfoService;
    }

    @GetMapping
    @Operation(summary = "分页查询机器人信息")
    public ApiResult<IPage<RobotInfoResp>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return success(robotInfoService.pageResp(pageNum, pageSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询机器人信息详情")
    public ApiResult<RobotInfo> get(@PathVariable Long id) {
        return success(required(id));
    }

    @PostMapping
    @Operation(summary = "新增或修改机器人信息")
    public ApiResult<RobotInfo> save(@RequestBody RobotInfoSaveDto request) {
        if (request.getId() == null) {
            RobotInfo entity = new RobotInfo();
            BeanUtils.copyProperties(request, entity);
            robotInfoService.save(entity);
            return created(entity);
        }

        RobotInfo entity = required(request.getId());
        BeanUtils.copyProperties(request, entity, "id");
        robotInfoService.updateById(entity);
        return success(required(entity.getId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除机器人信息")
    public ApiResult<Void> delete(@PathVariable Long id) {
        required(id);
        robotInfoService.removeById(id);
        return success();
    }

    private RobotInfo required(Long id) {
        RobotInfo entity = robotInfoService.getById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("机器人信息不存在: " + id);
        }
        return entity;
    }
}
