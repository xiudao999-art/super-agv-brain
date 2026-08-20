package com.kunling.scheduling.action.controller;

import com.kunling.scheduling.action.commissioning.application.ActionParameterSetService;
import com.kunling.scheduling.action.commissioning.application.ActionParameterSetView;
import com.kunling.scheduling.action.commissioning.application.SaveParameterSetRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@Tag(name = "设备联调参数", description = "维护机器人、工装和物料对应的详细动作参数")
@RestController
@RequestMapping("/api/action-parameter-sets")
public class ParameterSetController {
    private final ActionParameterSetService parameterSetService;

    public ParameterSetController(ActionParameterSetService parameterSetService) {
        this.parameterSetService = parameterSetService;
    }

    @Operation(summary = "查询 Action 的联调参数集")
    @GetMapping
    public List<ActionParameterSetView> list(
            @Parameter(description = "Action 唯一标识", example = "ARM.PICK")
            @RequestParam String actionKey) {
        return parameterSetService.list(actionKey);
    }

    @Operation(summary = "查询联调参数集详情")
    @GetMapping("/{id}")
    public ActionParameterSetView get(
            @Parameter(description = "联调参数集标识") @PathVariable String id) {
        return parameterSetService.get(id);
    }

    @Operation(summary = "新建联调参数集")
    @PostMapping
    public ResponseEntity<ActionParameterSetView> create(@RequestBody SaveParameterSetRequest request) {
        ActionParameterSetView created = parameterSetService.create(request);
        return ResponseEntity.created(URI.create("/api/action-parameter-sets/" + created.id())).body(created);
    }

    @Operation(summary = "修改联调参数集", description = "执行中的参数集不允许修改")
    @PutMapping("/{id}")
    public ActionParameterSetView update(
                                         @Parameter(description = "联调参数集标识") @PathVariable String id,
                                         @RequestBody SaveParameterSetRequest request) {
        return parameterSetService.update(id, request);
    }

    @Operation(summary = "删除联调参数集", description = "执行中的参数集不允许删除")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "联调参数集标识") @PathVariable String id,
            @Parameter(description = "当前并发控制 revision", example = "1")
            @RequestParam long expectedRevision) {
        parameterSetService.delete(id, expectedRevision);
        return ResponseEntity.noContent().build();
    }
}
