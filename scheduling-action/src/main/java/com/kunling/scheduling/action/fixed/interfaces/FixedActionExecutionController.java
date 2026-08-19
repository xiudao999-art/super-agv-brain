package com.kunling.scheduling.action.fixed.interfaces;

import com.kunling.scheduling.action.fixed.application.FixedActionExecutionService;
import com.kunling.scheduling.action.fixed.application.StartFixedActionExecutionRequest;
import com.kunling.scheduling.action.fixed.domain.RobotActionExecutionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

import static com.kunling.scheduling.action.interfaces.docs.ActionApiDocumentation.TAG_FIXED_ACTION;

@RestController
@RequestMapping("/api/v1/robot-action-executions")
@Tag(name = TAG_FIXED_ACTION, description = "下发一期固定动作包并查询或核对执行状态")
public class FixedActionExecutionController {

    private final FixedActionExecutionService executionService;

    public FixedActionExecutionController(FixedActionExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping
    @Operation(summary = "下发固定动作", description = "根据一期固定 JSON 模板组装完整动作包，并发送给指定机器人执行")
    @ApiResponse(
            responseCode = "202",
            description = "固定动作请求已受理",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RobotActionExecutionView.class))
    )
    public ResponseEntity<RobotActionExecutionView> start(@RequestBody StartFixedActionExecutionRequest request) {
        RobotActionExecutionView execution = executionService.start(request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/robot-action-executions/" + execution.actionInstanceId()))
                .body(execution);
    }

    @GetMapping("/{actionInstanceId}")
    @Operation(summary = "查询固定动作执行状态", description = "读取调度系统已持久化的动作执行状态，不主动请求机器人")
    @ApiResponse(
            responseCode = "200",
            description = "查询成功",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RobotActionExecutionView.class))
    )
    public RobotActionExecutionView get(
            @Parameter(description = "动作执行实例唯一标识", required = true, example = "action-20260819-001")
            @PathVariable String actionInstanceId
    ) {
        return executionService.get(actionInstanceId);
    }

    @PostMapping("/{actionInstanceId}/query")
    @Operation(summary = "主动核对固定动作状态", description = "向在线机器人发起状态查询；物理结果无法确认时保持 HOLD")
    @ApiResponse(
            responseCode = "202",
            description = "状态核对请求已受理",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RobotActionExecutionView.class))
    )
    public ResponseEntity<RobotActionExecutionView> query(
            @Parameter(description = "动作执行实例唯一标识", required = true, example = "action-20260819-001")
            @PathVariable String actionInstanceId
    ) {
        return ResponseEntity.accepted().body(executionService.query(actionInstanceId));
    }
}
