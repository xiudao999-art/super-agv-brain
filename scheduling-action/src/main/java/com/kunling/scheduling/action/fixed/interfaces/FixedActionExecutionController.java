package com.kunling.scheduling.action.fixed.interfaces;

import com.kunling.scheduling.action.fixed.application.FixedActionExecutionService;
import com.kunling.scheduling.action.fixed.application.StartFixedActionExecutionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/robot-action-executions")
public class FixedActionExecutionController {

    private final FixedActionExecutionService executionService;

    public FixedActionExecutionController(FixedActionExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping
    public ResponseEntity<?> start(@RequestBody StartFixedActionExecutionRequest request) {
        com.kunling.scheduling.action.fixed.domain.RobotActionExecutionView execution =
                executionService.start(request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/robot-action-executions/" + execution.actionInstanceId()))
                .body(execution);
    }

    @GetMapping("/{actionInstanceId}")
    public Object get(@PathVariable String actionInstanceId) {
        return executionService.get(actionInstanceId);
    }

    @PostMapping("/{actionInstanceId}/query")
    public ResponseEntity<?> query(@PathVariable String actionInstanceId) {
        return ResponseEntity.accepted().body(executionService.query(actionInstanceId));
    }
}
