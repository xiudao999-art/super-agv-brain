package com.kunling.scheduling.action.controller;

import com.kunling.scheduling.action.definition.application.ActionDefinitionService;
import com.kunling.scheduling.action.definition.application.ActionDefinitionView;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.common.audit.OperationLog;
import com.kunling.scheduling.common.audit.OperationType;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

import java.util.List;

/** 按定义 ID 管理 Action；不暴露 revision、Schema 或参数集兼容路径。 */
@Tag(name = "动作配置管理", description = "维护单个 Action 内的串行子动作编排")
@RestController
@RequestMapping("/api/actions")
public class ActionController extends BaseController {
    private final ActionDefinitionService definitionService;

    public ActionController(ActionDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @Operation(summary = "查询全部 Action")
    @GetMapping
    public ApiResult<List<ActionDefinitionView>> list() {
        return success(definitionService.list());
    }

    @Operation(summary = "按 ID 查询 Action")
    @GetMapping("/{id}")
    public ApiResult<ActionDefinitionView> get(@PathVariable String id) {
        return success(definitionService.get(id));
    }

    @Operation(summary = "新建 Action", description = "服务端生成 id，且新建定义固定为未启用")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<ActionDefinitionView> create(@RequestBody ActionDefinition definition) {
        return created(definitionService.create(definition));
    }

    @Operation(summary = "修改 Action", description = "运行中的定义不允许修改")
    @PutMapping("/{id}")
    public ApiResult<ActionDefinitionView> update(@PathVariable String id,
                                                   @RequestBody ActionDefinition definition) {
        return success(definitionService.update(id, definition));
    }

    @Operation(summary = "启用 Action", description = "使用指定在线机器人的注册能力完成启用校验")
    @PostMapping("/{id}/enable")
    @OperationLog(module = "动作配置", operation = "启用 Action", type = OperationType.PUBLISH,
            recordResponse = false)
    public ApiResult<ActionDefinitionView> enable(
            @PathVariable String id,
            @Parameter(description = "用于能力校验的在线机器人") @RequestParam String robotId) {
        return success(definitionService.enable(id, robotId));
    }

    @Operation(summary = "停用 Action")
    @PostMapping("/{id}/disable")
    @OperationLog(module = "动作配置", operation = "停用 Action", type = OperationType.UPDATE,
            recordResponse = false)
    public ApiResult<ActionDefinitionView> disable(@PathVariable String id) {
        return success(definitionService.disable(id));
    }

    @Operation(summary = "删除 Action", description = "运行中的定义不允许删除")
    @DeleteMapping("/{id}")
    @OperationLog(module = "动作配置", operation = "删除 Action", type = OperationType.DELETE,
            recordResponse = false)
    public ApiResult<Void> delete(@PathVariable String id) {
        definitionService.delete(id);
        return success();
    }
}
