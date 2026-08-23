package com.kunling.scheduling.agvflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kunling.scheduling.agvflow.domain.dto.*;
import com.kunling.scheduling.agvflow.service.FlowTemplateService;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/flow-templates")
@Tag(name = "流程模板管理", description = "创建流程模板并查询模板、节点及动作明细")
public class FlowTemplateController extends BaseController {
    @Resource
    private FlowTemplateService templateService;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新建流程模板")
    public ApiResult<Map<String, Long>> create(@Valid @RequestBody FlowTemplateCreateRequest request) {
        HashMap<String, Long> response = new HashMap<>();
        Long template = templateService.createTemplate(request);
        response.put("id",template);
        return created(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询模板详情")
    public ApiResult<FlowTemplateDetail> detail(@PathVariable Long id) {
        return success(templateService.getTemplateDetail(id));
    }

    @GetMapping
    @Operation(summary = "分页查询模板列表")
    public ApiResult<Page<FlowTemplateListItem>> pageList(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return success(templateService.pageTemplates(current, size, keyword));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑流程模板")
    public ApiResult<FlowTemplateDetail> update(@PathVariable Long id,
                                                @Valid @RequestBody FlowTemplateUpdateRequest request) {
        return success(templateService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除流程模板")
    public ApiResult<Void> delete(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return success();
    }


    @GetMapping("/startDemo")
    @Operation(summary = "测试流程开始", description = "用于和下游联调测试,非正式环境内容,后续根据上游实际功能代码")
    public ApiResult<Map<String, Integer>> start(@RequestParam Long id) {
        templateService.startFlow(id);
        return success(java.util.Collections.singletonMap("id", 200));
    }

    @PostMapping("/external/skip-hang-node")
    @Operation(summary = "跳过当前流程的挂起节点并执行下一节点")
    public ApiResult<Map<String, Object>> skipHangNode() {
        Long nextNodeId = templateService.skipHangNodeAndStartNext();
        Map<String, Object> response = new HashMap<>();
        response.put("completed", nextNodeId == null);
        response.put("nextNodeId", nextNodeId);
        return success(response);
    }
}
