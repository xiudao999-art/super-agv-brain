package com.kunling.scheduling.app.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.app.domain.ActionParameterSchema;
import com.kunling.scheduling.app.domain.ActionParameterSchema.ParameterOwnerType;
import com.kunling.scheduling.app.domain.ActionParameterSchema.SaveRequest;
import com.kunling.scheduling.app.domain.ActionParameterSchema.ValidationResult;
import com.kunling.scheduling.app.service.ActionParameterSchemaService;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 主 Action 与子 Action 共用的动态入参配置接口。 */
@RestController
@RequestMapping("/api/action-parameter-schemas")
@Tag(name = "Action 参数配置", description = "查询、覆盖保存并校验 Action 动态入参 Schema")
public class ActionParameterSchemaController extends BaseController {

    private final ActionParameterSchemaService schemaService;

    public ActionParameterSchemaController(ActionParameterSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @GetMapping("/{ownerType}/{ownerKey}")
    @Operation(summary = "查询 Action 参数 Schema", description = "未配置时返回空字段集合")
    public ApiResult<ActionParameterSchema> get(
            @PathVariable ParameterOwnerType ownerType,
            @PathVariable String ownerKey) {
        return success(schemaService.get(ownerType, ownerKey));
    }

    @PutMapping("/{ownerType}/{ownerKey}")
    @Operation(summary = "覆盖保存 Action 参数 Schema", description = "空 fields 表示没有动态参数")
    public ApiResult<ActionParameterSchema> save(
            @PathVariable ParameterOwnerType ownerType,
            @PathVariable String ownerKey,
            @RequestBody SaveRequest request) {
        return success(schemaService.save(ownerType, ownerKey, request));
    }

    @PostMapping("/{ownerType}/{ownerKey}/validate")
    @Operation(summary = "校验 Action 参数值", description = "一次返回全部字段问题，不执行类型转换")
    public ApiResult<ValidationResult> validate(
            @PathVariable ParameterOwnerType ownerType,
            @PathVariable String ownerKey,
            @RequestBody JsonNode parameterValues) {
        return success(schemaService.validate(ownerType, ownerKey, parameterValues));
    }
}
