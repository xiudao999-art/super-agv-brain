package com.kunling.scheduling.action.controller;

import com.kunling.scheduling.action.commissioning.application.ArmPositionProbeResult;
import com.kunling.scheduling.action.commissioning.application.ArmPositionProbeService;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Action 调试探测", description = "只读查询设备当前状态，不创建业务 Action 执行")
@RestController
@RequestMapping("/api/action-debug")
public class ActionDebugController extends BaseController {
    private final ArmPositionProbeService probeService;

    public ActionDebugController(ArmPositionProbeService probeService) {
        this.probeService = probeService;
    }

    @Operation(summary = "获取机械臂当前位姿",
            description = "下发 armCommandModelType=3 的只读 MOVE_TO_POSE 单步骤探测")
    @PostMapping("/robots/{robotId}/arm-position")
    public ApiResult<ArmPositionProbeResult> armPosition(
            @Parameter(description = "当前在线机器人 ID") @PathVariable String robotId) {
        return success(probeService.probe(robotId));
    }
}
