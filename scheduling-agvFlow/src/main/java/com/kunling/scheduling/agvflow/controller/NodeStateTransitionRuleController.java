package com.kunling.scheduling.agvflow.controller;


import com.kunling.scheduling.agvflow.domain.dto.StatusChangedDto;
import com.kunling.scheduling.agvflow.domain.entity.NodeStateTransitionRule;
import com.kunling.scheduling.agvflow.service.NodeStateTransitionRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/nodeRules")
@Tag(name = "节点状态流转规则", description = "维护节点状态机规则并处理节点状态流转事件")
public class NodeStateTransitionRuleController {

    @Resource
    private NodeStateTransitionRuleService service;

    public NodeStateTransitionRuleController(NodeStateTransitionRuleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "查询规则列表")
    public List<NodeStateTransitionRule> list(
            @Parameter(description = "规则集编码") @RequestParam(required = false) String ruleSetCode,
            @Parameter(description = "当前节点状态") @RequestParam(required = false) String currentState,
            @Parameter(description = "触发事件编码") @RequestParam(required = false) String eventCode,
            @Parameter(description = "启用标记：1 启用，0 停用", example = "1")
            @RequestParam(required = false) Integer enabled) {
        return service.listRules(ruleSetCode, currentState, eventCode, enabled);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询规则详情")
    public NodeStateTransitionRule get(
            @Parameter(description = "流转规则 ID", example = "1") @PathVariable Long id) {
        return service.getRule(id);
    }

    @PostMapping
    @Operation(summary = "新增规则")
    public ResponseEntity<NodeStateTransitionRule> create(@RequestBody NodeStateTransitionRule rule) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRule(rule));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改规则")
    public NodeStateTransitionRule update(
            @Parameter(description = "流转规则 ID", example = "1") @PathVariable Long id,
            @RequestBody NodeStateTransitionRule rule) {
        return service.updateRule(id, rule);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除规则")
    public ResponseEntity<Void> delete(
            @Parameter(description = "流转规则 ID", example = "1") @PathVariable Long id) {
        service.deleteRule(id);
        return ResponseEntity.noContent().build();
    }


/*    @DeleteMapping("/changed")
    @Operation(summary = "批量处理状态流转", description = "根据节点当前状态和事件编码匹配规则并推进状态")
    public ResponseEntity<Void> statusChanged(@RequestBody List<StatusChangedDto> dto) {
        service.statusChanged(dto);
        return ResponseEntity.noContent().build();
    }*/
}
