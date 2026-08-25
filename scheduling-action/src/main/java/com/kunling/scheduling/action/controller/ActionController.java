package com.kunling.scheduling.action.controller;

import com.kunling.scheduling.action.definition.application.ActionDefinitionService;
import com.kunling.scheduling.action.definition.application.ActionDefinitionView;
import com.kunling.scheduling.action.definition.application.SaveActionDefinitionRequest;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/** 当前 Action 定义接口；不再暴露 clone、release、diff 等业务版本操作。 */
@Tag(name = "动作配置管理", description = "维护当前 Action 的步骤、参数约束及启停状态")
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

    @Operation(summary = "查询 Action 详情")
    @GetMapping("/{actionKey}")
    public ApiResult<ActionDefinitionView> get(
            @Parameter(description = "Action 唯一标识", example = "ARM.PICK")
            @PathVariable String actionKey) {
        return success(definitionService.get(actionKey));
    }

    @Operation(summary = "新建 Action 草稿", description = "新建时 expectedRevision 必须为空")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<ActionDefinitionView> create(@RequestBody SaveActionDefinitionRequest request) {
        if (request.expectedRevision() != null) {
            throw new IllegalArgumentException("新建 Action 不能携带 expectedRevision。");
        }
        return created(definitionService.create(request.definition()));
    }

    @Operation(summary = "修改 Action 草稿", description = "修改后状态回到 DRAFT，执行中的 Action 不允许修改")
    @PutMapping("/{actionKey}")
    public ApiResult<ActionDefinitionView> update(
                                       @Parameter(description = "Action 唯一标识", example = "ARM.PICK")
                                       @PathVariable String actionKey,
                                       @RequestBody SaveActionDefinitionRequest request) {
        if (request.expectedRevision() == null) {
            throw new IllegalArgumentException("更新 Action 必须携带 expectedRevision。");
        }
        return success(definitionService.update(actionKey, request.expectedRevision(), request.definition()));
    }

    @Operation(summary = "启用 Action", description = "启用前执行完整结构与参数绑定校验")
    @PostMapping("/{actionKey}/activate")
    @OperationLog(module = "动作配置", operation = "启用 Action", type = OperationType.PUBLISH,
            recordResponse = false)
    public ApiResult<ActionDefinitionView> activate(
                                         @Parameter(description = "Action 唯一标识", example = "ARM.PICK")
                                         @PathVariable String actionKey,
                                         @Parameter(description = "当前并发控制 revision", example = "1")
                                         @RequestParam long expectedRevision) {
        return success(definitionService.activate(actionKey, expectedRevision));
    }

    @Operation(summary = "停用 Action")
    @PostMapping("/{actionKey}/disable")
    @OperationLog(module = "动作配置", operation = "停用 Action", type = OperationType.UPDATE,
            recordResponse = false)
    public ApiResult<ActionDefinitionView> disable(
                                        @Parameter(description = "Action 唯一标识", example = "ARM.PICK")
                                        @PathVariable String actionKey,
                                        @Parameter(description = "当前并发控制 revision", example = "1")
                                        @RequestParam long expectedRevision) {
        return success(definitionService.disable(actionKey, expectedRevision));
    }

    @Operation(summary = "删除 Action", description = "执行中的 Action 不允许删除")
    @DeleteMapping("/{actionKey}")
    @OperationLog(module = "动作配置", operation = "删除 Action", type = OperationType.DELETE,
            recordResponse = false)
    public ApiResult<Void> delete(
                                       @Parameter(description = "Action 唯一标识", example = "ARM.PICK")
                                       @PathVariable String actionKey,
                                       @Parameter(description = "当前并发控制 revision", example = "1")
                                       @RequestParam long expectedRevision) {
        definitionService.delete(actionKey, expectedRevision);
        return success();
    }
}
