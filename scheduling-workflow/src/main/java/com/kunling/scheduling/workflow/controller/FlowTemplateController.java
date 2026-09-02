package com.kunling.scheduling.workflow.controller;

import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import com.kunling.scheduling.workflow.dto.FlowTemplateCreateRequest;
import com.kunling.scheduling.workflow.dto.FlowTemplateUpdateRequest;
import com.kunling.scheduling.workflow.dto.WorkflowTemplateResponses;
import com.kunling.scheduling.workflow.service.FlowTemplateService;
import com.kunling.scheduling.workflow.service.WorkflowTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flow-templates")
@Tag(name = "流程列表管理", description = "创建列表模板并查询模板、节点及动作明细")
public class FlowTemplateController extends BaseController {
    @Resource
    private FlowTemplateService templateService;
    @Resource
    private WorkflowTemplateService service;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新建流程模板")
    public ApiResult<Map<String, Long>> create(@Valid @RequestBody FlowTemplateCreateRequest request) {
        HashMap<String, Long> response = new HashMap<>();
        Long template = templateService.createTemplate(request);
        response.put("id",template);
        return created(response);
    }

    @GetMapping("/flows/page")
    @Operation(summary = "分页查询流程列表，支持按流程名称或模板名称搜索")
    public ApiResult<WorkflowTemplateResponses.FlowPage> flowPage(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword) {
        return ApiResult.success(service.flowPage(pageNum, pageSize, keyword));
    }

    @GetMapping("/flows/{id}")
    @Operation(summary = "查询流程编辑回显数据")
    public ApiResult<WorkflowTemplateResponses.FlowDetail> flowDetail(@PathVariable Long id) {
        return success(templateService.getFlow(id));
    }

    @PutMapping("/flows/{id}")
    @Operation(summary = "编辑流程列表中的流程")
    public ApiResult<WorkflowTemplateResponses.FlowDetail> updateFlow(
            @PathVariable Long id, @Valid @RequestBody FlowTemplateUpdateRequest request) {
        return success(templateService.updateFlow(id, request));
    }


    @GetMapping("/list")
    @Operation(summary = "查询流程模板列表")
    public ApiResult<List<WorkflowTemplateResponses.FlowPageItem>> list() {
        return ApiResult.success(templateService.flowTemplateList());
    }
}
