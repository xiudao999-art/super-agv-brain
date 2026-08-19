package com.kunling.scheduling.action.fixed.interfaces;

import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import com.kunling.scheduling.action.robotbridge.application.RobotSessionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.kunling.scheduling.action.interfaces.docs.ActionApiDocumentation.TAG_ROBOT_SESSION;

import java.util.List;

@RestController
@RequestMapping("/api/v1/robots")
@Tag(name = TAG_ROBOT_SESSION, description = "查询主动连接到调度系统的机器人会话")
public class RobotSessionController {

    private final RobotActionTransport transport;

    public RobotSessionController(RobotActionTransport transport) {
        this.transport = transport;
    }

    @GetMapping
    @Operation(summary = "查询在线机器人", description = "返回已完成注册且当前租约有效的机器人连接列表")
    @ApiResponse(
            responseCode = "200",
            description = "查询成功",
            content = @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = RobotSessionView.class))
            )
    )
    public List<RobotSessionView> listConnectedRobots() {
        return transport.listSessions();
    }
}
