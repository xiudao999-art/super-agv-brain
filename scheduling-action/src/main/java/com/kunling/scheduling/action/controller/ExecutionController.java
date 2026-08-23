package com.kunling.scheduling.action.controller;

import com.kunling.scheduling.action.execution.application.ActionExecutionService;
import com.kunling.scheduling.action.execution.application.ActionPackagePreview;
import com.kunling.scheduling.action.execution.application.StartActionExecutionRequest;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.execution.domain.ActionExecutionEventView;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@Tag(name = "完整动作包执行", description = "预览、冻结、下发并查询下游完整动作包")
@RestController
@RequestMapping("/api/action-executions")
public class ExecutionController extends BaseController {
    private final ActionExecutionService executionService;

    public ExecutionController(ActionExecutionService executionService) {
        this.executionService = executionService;
    }

    @Operation(summary = "预览完整动作包", description = "解析本次全部参数并返回只读快照及 packageHash，不会下发设备")
    @PostMapping("/preview")
    public ApiResult<ActionPackagePreview> preview(@RequestBody StartActionExecutionRequest request) {
        return success(executionService.preview(request));
    }

    @Operation(summary = "开始执行完整动作包", description = "必须携带预览返回的 expectedPackageHash；开始后当前任务不可编辑")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResult<ActionExecutionView> start(@RequestBody StartActionExecutionRequest request) {
        return accepted(executionService.start(request));
    }

    @Operation(summary = "查询动作执行详情")
    @GetMapping("/{actionInstanceId}")
    public ApiResult<ActionExecutionView> get(
            @Parameter(description = "动作执行实例标识") @PathVariable String actionInstanceId) {
        return success(executionService.get(actionInstanceId));
    }

    @Operation(summary = "查询动作执行事件", description = "按服务端接收顺序返回下游推送的 phase 进度、设备证据和原始异常")
    @GetMapping("/{actionInstanceId}/events")
    public ApiResult<List<ActionExecutionEventView>> events(
            @Parameter(description = "动作执行实例标识") @PathVariable String actionInstanceId,
            @Parameter(description = "最多返回事件数，范围 1 到 1000")
            @RequestParam(defaultValue = "500") int limit) {
        return success(executionService.getEvents(actionInstanceId, limit));
    }

    @Operation(summary = "查询 Action 的活动执行", description = "没有活动执行时返回成功 Result，data 为空")
    @GetMapping("/active")
    public ApiResult<ActionExecutionView> active(
            @Parameter(description = "Action 唯一标识", example = "ARM.PICK")
            @RequestParam String actionKey) {
        return success(executionService.findActiveForAction(actionKey).orElse(null));
    }

    @Operation(summary = "查询下游物理执行结果", description = "仅用于 UNKNOWN_HOLD 等结果不确定场景，不会重新下发动作")
    @PostMapping("/{actionInstanceId}/query")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResult<ActionExecutionView> query(
            @Parameter(description = "动作执行实例标识") @PathVariable String actionInstanceId) {
        return accepted(executionService.query(actionInstanceId));
    }
}
