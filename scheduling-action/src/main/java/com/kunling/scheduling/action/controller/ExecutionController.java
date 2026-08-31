package com.kunling.scheduling.action.controller;

import com.kunling.scheduling.action.execution.application.ActionExecutionService;
import com.kunling.scheduling.action.execution.application.ActionPackagePreview;
import com.kunling.scheduling.action.execution.application.ActionPackagePreviewRequest;
import com.kunling.scheduling.action.execution.application.ActionExecutionReceipt;
import com.kunling.scheduling.action.execution.application.ExecuteActionCommand;
import com.kunling.scheduling.action.execution.domain.ActionExecutionView;
import com.kunling.scheduling.action.execution.domain.ActionExecutionEventView;
import com.kunling.scheduling.common.audit.OperationLog;
import com.kunling.scheduling.common.audit.OperationType;
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

@Tag(name = "完整动作包执行", description = "预览、冻结、下发并读取本地执行事实")
@RestController
@RequestMapping("/api/action-executions")
public class ExecutionController extends BaseController {
    private final ActionExecutionService executionService;

    public ExecutionController(ActionExecutionService executionService) {
        this.executionService = executionService;
    }

    @Operation(summary = "预览完整动作包", description = "校验当前在线机器人能力并返回组包结果，不会下发设备")
    @PostMapping("/preview")
    public ApiResult<ActionPackagePreview> preview(@RequestBody ActionPackagePreviewRequest request) {
        return success(executionService.preview(request));
    }

    @Operation(summary = "开始执行完整动作包", description = "同一 actionInstanceId 最多下发一次；开始后当前 Action 不可编辑")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @OperationLog(module = "动作执行", operation = "开始执行完整动作包", type = OperationType.EXECUTE,
            recordResponse = false)
    public ApiResult<ActionExecutionReceipt> start(@RequestBody ExecuteActionCommand command) {
        return accepted(executionService.execute(command));
    }

    @Operation(summary = "查询动作执行详情")
    @GetMapping("/{actionInstanceId}")
    public ApiResult<ActionExecutionView> get(
            @Parameter(description = "动作执行实例标识") @PathVariable String actionInstanceId) {
        return success(executionService.get(actionInstanceId));
    }

    @Operation(summary = "查询动作执行事件", description = "按服务端接收顺序返回下游推送的 step 进度、设备证据和原始异常")
    @GetMapping("/{actionInstanceId}/events")
    public ApiResult<List<ActionExecutionEventView>> events(
            @Parameter(description = "动作执行实例标识") @PathVariable String actionInstanceId,
            @Parameter(description = "最多返回事件数，范围 1 到 1000")
            @RequestParam(defaultValue = "500") int limit) {
        return success(executionService.getEvents(actionInstanceId, limit));
    }

    @Operation(summary = "查询 Action 定义的活动执行", description = "没有活动执行时 data 为空")
    @GetMapping("/active")
    public ApiResult<ActionExecutionView> active(
            @Parameter(description = "Action 定义 ID")
            @RequestParam String actionDefinitionId) {
        return success(executionService.findActiveForAction(actionDefinitionId).orElse(null));
    }

}
