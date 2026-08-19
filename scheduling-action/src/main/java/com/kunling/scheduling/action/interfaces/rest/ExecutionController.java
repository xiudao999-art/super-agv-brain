package com.kunling.scheduling.action.interfaces.rest;

import com.kunling.scheduling.action.execution.application.ActionExecutionService;
import com.kunling.scheduling.action.execution.application.ActionExecutionView;
import com.kunling.scheduling.action.execution.application.StartActionExecutionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

import static com.kunling.scheduling.action.interfaces.docs.ActionApiDocumentation.TAG_DYNAMIC_EXECUTION;

@RestController
@RequestMapping("/api/action-executions")
@ConditionalOnProperty(prefix = "kunling.action.dynamic-execution", name = "enabled", havingValue = "true")
@Tag(name = TAG_DYNAMIC_EXECUTION, description = "二期动态动作执行接口，当前一期默认关闭")
public class ExecutionController {

    private final ActionExecutionService executionService;

    public ExecutionController(ActionExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping
    @Operation(summary = "启动动态动作执行", description = "根据精确发布版本创建动态动作执行实例；一期默认不启用")
    @ApiResponse(
            responseCode = "202",
            description = "动态动作执行请求已受理",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ActionExecutionView.class))
    )
    public ResponseEntity<ActionExecutionView> start(@RequestBody StartActionExecutionRequest request) {
        ActionExecutionView execution = executionService.start(request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/action-executions/" + execution.actionInstanceId()))
                .body(execution);
    }

    @GetMapping("/{actionInstanceId}")
    @Operation(summary = "查询动态动作执行状态", description = "查询动态动作执行实例及其节点状态")
    @ApiResponse(
            responseCode = "200",
            description = "查询成功",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ActionExecutionView.class))
    )
    public ActionExecutionView get(
            @Parameter(description = "动作执行实例唯一标识", required = true) @PathVariable String actionInstanceId
    ) {
        return executionService.get(actionInstanceId);
    }

    @PostMapping("/{actionInstanceId}/cancel")
    @Operation(summary = "请求取消动态动作执行", description = "登记取消请求；不会主动取消已发送给机器人的动作包")
    @ApiResponse(
            responseCode = "200",
            description = "取消请求登记成功",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ActionExecutionView.class))
    )
    public ActionExecutionView cancel(
            @Parameter(description = "动作执行实例唯一标识", required = true) @PathVariable String actionInstanceId
    ) {
        return executionService.cancel(actionInstanceId);
    }
}
