package com.kunling.scheduling.action.interfaces.rest;

import com.kunling.scheduling.action.execution.application.ActionExecutionService;
import com.kunling.scheduling.action.execution.application.StartActionExecutionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/action-executions")
public class ExecutionController {

    private final ActionExecutionService executionService;

    public ExecutionController(ActionExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping
    public ResponseEntity<?> start(@RequestBody StartActionExecutionRequest request) {
        var execution = executionService.start(request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/action-executions/" + execution.actionInstanceId()))
                .body(execution);
    }

    @GetMapping("/{actionInstanceId}")
    public Object get(@PathVariable String actionInstanceId) {
        return executionService.get(actionInstanceId);
    }

    @PostMapping("/{actionInstanceId}/cancel")
    public Object cancel(@PathVariable String actionInstanceId) {
        return executionService.cancel(actionInstanceId);
    }
}
