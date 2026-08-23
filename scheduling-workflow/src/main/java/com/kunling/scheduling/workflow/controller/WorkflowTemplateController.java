package com.kunling.scheduling.workflow.controller;

import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.dto.WorkflowTemplateRequests;
import com.kunling.scheduling.workflow.dto.WorkflowTemplateResponses;
import com.kunling.scheduling.workflow.service.WorkflowTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/workflow-templates")
@Tag(name = "BPMN流程模板")
public class WorkflowTemplateController extends BaseController {
    private final WorkflowTemplateService service;
    public WorkflowTemplateController(WorkflowTemplateService service) { this.service = service; }

    @PostMapping("/deploy")
    @Operation(summary = "将已保存模板部署到Flowable,发布模版")
    public ApiResult<WorkflowResponses.Definition> deploy(@Parameter Long id) {
        return success(service.deploy(id));
    }


    @PostMapping
    @Operation(summary = "保存bpmn.js流程模板")
    public ApiResult<WorkflowTemplateResponses.Detail> create(
            @Valid @RequestBody WorkflowTemplateRequests.Save request) {
        return success(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新bpmn.js流程模板")
    public ApiResult<WorkflowTemplateResponses.Detail> update(
            @PathVariable Long id, @Valid @RequestBody WorkflowTemplateRequests.Save request) {
        return success(service.update(id, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询模板完整数据用于页面回显")
    public ApiResult<WorkflowTemplateResponses.Detail> detail(@PathVariable Long id) {
        return success(service.get(id));
    }

    @GetMapping
    @Operation(summary = "查询模板列表")
    public ApiResult<List<WorkflowTemplateResponses.Summary>> list(
            @RequestParam(required = false) String keyword) {
        return success(service.list(keyword));
    }

    @GetMapping("/{id}/xml")
    @Operation(summary = "获取bpmn.js可重新导入的BPMN XML")
    public ApiResult<String> xml(@PathVariable Long id) {
        return success(service.get(id).getBpmnXml());
    }



    @DeleteMapping("/{id}")
    @Operation(summary = "删除业务模板，不删除Flowable历史")
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return success();
    }
}
