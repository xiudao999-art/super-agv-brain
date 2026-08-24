package com.kunling.scheduling.workflow.controller;

import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import com.kunling.scheduling.workflow.dto.WorkflowRequests;
import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/workflows")
@Tag(name = "工作流程管理")
public class WorkflowController extends BaseController {
    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping("/definitions/deploy")
    @Operation(summary = "保存并部署bpmn.js导出的BPMN XML")
    public ApiResult<WorkflowResponses.Definition> deploy(
            @Valid @RequestBody WorkflowRequests.DeployDefinition request) {
        return success(workflowService.deploy(request));
    }

    @PostMapping("/instances")
    @Operation(summary = "启动流程实例")
    public ApiResult<WorkflowResponses.Instance> start(@RequestBody WorkflowRequests.StartInstance request) {
        return success(workflowService.start(request));
    }

    @PostMapping("/executions/trigger")
    @Operation(summary = "触发ReceiveTask等等待节点继续推进")
    public ApiResult<WorkflowResponses.Instance> trigger(
            @RequestBody WorkflowRequests.TriggerExecution request) {
        return success(workflowService.trigger(request));
    }

    @GetMapping("/instances/{id}")
    @Operation(summary = "查询流程实例状态")
    public ApiResult<WorkflowResponses.Instance> instance(@PathVariable String id) {
        return success(workflowService.getInstance(id));
    }


    @GetMapping("/definitions")
    @Operation(summary = "查询最新版本流程定义")
    public ApiResult<List<WorkflowResponses.Definition>> definitions(
            @RequestParam(required = false) String key) {
        return success(workflowService.listDefinitions(key));
    }

    @GetMapping("/definitions/{id}/xml")
    @Operation(summary = "获取流程定义BPMN XML")
    public ApiResult<String> definitionXml(@PathVariable String id) {
        return success(workflowService.getDefinitionXml(id));
    }


    @PostMapping("/instances/{id}/suspend")
    @Operation(summary = "挂起流程实例")
    public ApiResult<WorkflowResponses.Instance> suspend(@PathVariable String id) {
        return success(workflowService.suspend(id));
    }

    @PostMapping("/instances/{id}/activate")
    @Operation(summary = "恢复流程实例")
    public ApiResult<WorkflowResponses.Instance> activate(@PathVariable String id) {
        return success(workflowService.activate(id));
    }

    @PostMapping("/instances/{id}/terminate")
    @Operation(summary = "人工终止流程实例")
    public ApiResult<WorkflowResponses.Instance> terminate(
            @PathVariable String id, @RequestBody WorkflowRequests.TerminateInstance request) {
        return success(workflowService.terminate(id, request));
    }

    @GetMapping("/instances/active-nodes")
    @Operation(summary = "查询当前运行节点")
    public ApiResult<List<WorkflowResponses.ActiveNode>> activeNodes(@Parameter String id) {
        return success(workflowService.listActiveNodes(id));
    }



    @GetMapping("/instances/{id}/history")
    @Operation(summary = "查询全部历史节点状态")
    public ApiResult<List<WorkflowResponses.HistoryNode>> history(@PathVariable String id) {
        return success(workflowService.listHistory(id));
    }

    @GetMapping("/tasks")
    @Operation(summary = "查询人工异常处理任务")
    public ApiResult<List<WorkflowResponses.UserTask>> tasks(
            @RequestParam(required = false) String processInstanceId,
            @RequestParam(required = false) String assignee) {
        return success(workflowService.listTasks(processInstanceId, assignee));
    }

    @PostMapping("/tasks/{taskId}/claim")
    @Operation(summary = "签收人工任务")
    public ApiResult<Void> claim(@PathVariable String taskId,
                                 @Valid @RequestBody WorkflowRequests.ClaimTask request) {
        workflowService.claimTask(taskId, request);
        return success();
    }

    @PostMapping("/tasks/{taskId}/complete")
    @Operation(summary = "完成人工任务并推进流程")
    public ApiResult<Void> complete(@PathVariable String taskId,
                                    @RequestBody WorkflowRequests.CompleteTask request) {
        workflowService.completeTask(taskId, request);
        return success();
    }
}
