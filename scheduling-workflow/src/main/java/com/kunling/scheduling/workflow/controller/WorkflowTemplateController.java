package com.kunling.scheduling.workflow.controller;

import com.kunling.scheduling.common.audit.OperationLog;
import com.kunling.scheduling.common.audit.OperationType;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import com.kunling.scheduling.workflow.dto.FlowStartRequest;
import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.dto.WorkflowTemplateRequests;
import com.kunling.scheduling.workflow.dto.WorkflowTemplateResponses;
import com.kunling.scheduling.workflow.service.FlowControlService;
import com.kunling.scheduling.workflow.service.WorkflowTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/workflow-templates")
@Tag(name = "BPMN流程模板")
public class WorkflowTemplateController extends BaseController {
    @Resource
    FlowControlService flowControlService;
    private final WorkflowTemplateService service;
    public WorkflowTemplateController(WorkflowTemplateService service) { this.service = service; }

    @PostMapping("/deploy")
    @Operation(summary = "将已保存模板部署到Flowable,发布模版")
    @OperationLog(module = "流程模板", operation = "发布流程模板", type = OperationType.PUBLISH,
            recordResponse = false)
    public ApiResult<WorkflowResponses.Definition> deploy(@Parameter Long id) {
        return success(service.deploy(id));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布流程模板；已发布模板再次发布时生成Flowable新版本")
    @OperationLog(module = "流程模板", operation = "发布流程模板新版本", type = OperationType.PUBLISH,
            recordResponse = false)
    public ApiResult<WorkflowResponses.Definition> publish(@PathVariable Long id) {
        return success(service.deploy(id));
    }


    @PostMapping
    @Operation(summary = "保存bpmn.js流程模板")
    public ApiResult<WorkflowTemplateResponses.Detail> create(
            @Valid @RequestBody WorkflowTemplateRequests.Save request) {
        return success(service.create(request));
    }

    @PostMapping("/update")
    @Operation(summary = "保存流程模板编辑稿；已发布模板编辑后转为草稿")
    public ApiResult<WorkflowTemplateResponses.Detail> update(@Valid @RequestBody WorkflowTemplateRequests.Save request) {
        return success(service.update(request));
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

    @GetMapping("/page")
    @Operation(summary = "分页查询模板列表，包含BPMN动作顺序、版本和状态")
    public WorkflowTemplateResponses.Page page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword) {
        return service.page(pageNum, pageSize, keyword);
    }

    @GetMapping(value = "/{id}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "获取bpmn.js可重新导入的BPMN XML")
    public ApiResult<String> xml(@PathVariable Long id) {
        return success(service.get(id).getBpmnXml());
    }



    @DeleteMapping("/{id}")
    @Operation(summary = "删除业务模板，不删除Flowable历史")
    @OperationLog(module = "流程模板", operation = "删除流程模板", type = OperationType.DELETE,
            recordResponse = false)
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return success();
    }

    @PostMapping("/start")
    @Operation(summary = "启动流程")
    @OperationLog(module = "工作流程", operation = "从模板启动流程", type = OperationType.EXECUTE,
            recordResponse = false)
    public ApiResult<Void> start(@RequestBody FlowStartRequest flowStartRequest) {
        flowControlService.start(flowStartRequest);
        return success();
    }
}
