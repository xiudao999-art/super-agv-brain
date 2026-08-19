package com.kunling.scheduling.action.fixed.interfaces;

import com.kunling.scheduling.action.robotbridge.application.RobotActionTransport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/robots")
public class RobotSessionController {

    private final RobotActionTransport transport;

    public RobotSessionController(RobotActionTransport transport) {
        this.transport = transport;
    }

    @GetMapping
    public Object listConnectedRobots() {
        return transport.listSessions();
    }
}
