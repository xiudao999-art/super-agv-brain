package com.kunling.scheduling.agvflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kunling.scheduling.agvflow.domain.dto.*;
import com.kunling.scheduling.agvflow.service.FlowTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/flow-templates")
@Tag(name = "流程模板管理", description = "创建流程模板并查询模板、节点及动作明细")
public class FlowTemplateController {
    @Resource
    private FlowTemplateService templateService;

    @PostMapping("/create")
    @Operation(summary = "新建流程模板")
    public ResponseEntity<Map<String, Long>> create(@Valid @RequestBody FlowTemplateCreateRequest request) {
        HashMap<String, Long> response = new HashMap<>();
        Long template = templateService.createTemplate(request);
        response.put("id",template);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询模板详情")
    public FlowTemplateDetail detail(@PathVariable Long id) {
        return templateService.getTemplateDetail(id);
    }

    @GetMapping
    @Operation(summary = "分页查询模板列表")
    public Page<FlowTemplateListItem> pageList(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return templateService.pageTemplates(current, size, keyword);
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑流程模板")
    public FlowTemplateDetail update(@PathVariable Long id,
                                     @Valid @RequestBody FlowTemplateUpdateRequest request) {
        return templateService.updateTemplate(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除流程模板")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/startDemo")
    @Operation(summary = "测试流程开始", description = "用于和下游联调测试,非正式环境内容,后续根据上游实际功能代码")
    public Map<String, Integer> start(@RequestParam Long id) {
        templateService.startFlow(id);
        return java.util.Collections.singletonMap("id", 200);
    }

    @PostMapping("/external/skip-hang-node")
    @Operation(summary = "跳过当前流程的挂起节点并执行下一节点")
    public Map<String, Object> skipHangNode() {
        Long nextNodeId = templateService.skipHangNodeAndStartNext();
        Map<String, Object> response = new HashMap<>();
        response.put("completed", nextNodeId == null);
        response.put("nextNodeId", nextNodeId);
        return response;
    }
}
