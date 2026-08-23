package com.kunling.scheduling.action.controller;

import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "机器人连接管理", description = "查询主动连接并注册到调度系统的机器人")
@RestController
@RequestMapping("/api/robots")
public class RobotSessionController extends BaseController {
    private final RobotActionTransport transport;

    public RobotSessionController(RobotActionTransport transport) {
        this.transport = transport;
    }

    @Operation(summary = "查询在线机器人")
    @GetMapping
    public ApiResult<List<RobotSessionView>> listConnectedRobots() {
        return success(transport.listSessions());
    }
}
