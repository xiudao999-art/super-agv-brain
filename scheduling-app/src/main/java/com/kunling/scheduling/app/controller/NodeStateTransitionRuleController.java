package com.kunling.scheduling.app.controller;//package com.kunling.scheduling.app.controller;


import com.kunling.scheduling.workflow.entity.NodeStateTransitionRule;
import com.kunling.scheduling.workflow.service.NodeStateTransitionRuleService;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/nodeRules")
@Tag(name = "节点状态流转规则", description = "维护节点状态机规则并处理节点状态流转事件")
public class NodeStateTransitionRuleController extends BaseController {

    @Resource
    private NodeStateTransitionRuleService service;

    public NodeStateTransitionRuleController(NodeStateTransitionRuleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "查询规则列表")
    public ApiResult<List<NodeStateTransitionRule>> list(
            @Parameter(description = "规则集编码") @RequestParam(required = false) String ruleSetCode,
            @Parameter(description = "当前节点状态") @RequestParam(required = false) String currentState,
            @Parameter(description = "触发事件编码") @RequestParam(required = false) String eventCode,
            @Parameter(description = "启用标记：1 启用，0 停用", example = "1")
            @RequestParam(required = false) Integer enabled) {
        return success(service.listRules(ruleSetCode, currentState, eventCode, enabled));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询规则详情")
    public ApiResult<NodeStateTransitionRule> get(
            @Parameter(description = "流转规则 ID", example = "1") @PathVariable Long id) {
        return success(service.getRule(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增规则")
    public ApiResult<NodeStateTransitionRule> create(@RequestBody NodeStateTransitionRule rule) {
        return created(service.createRule(rule));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改规则")
    public ApiResult<NodeStateTransitionRule> update(
            @Parameter(description = "流转规则 ID", example = "1") @PathVariable Long id,
            @RequestBody NodeStateTransitionRule rule) {
        return success(service.updateRule(id, rule));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除规则")
    public ApiResult<Void> delete(
            @Parameter(description = "流转规则 ID", example = "1") @PathVariable Long id) {
        service.deleteRule(id);
        return success();
    }
}
