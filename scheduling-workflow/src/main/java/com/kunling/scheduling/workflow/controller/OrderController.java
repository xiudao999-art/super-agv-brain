package com.kunling.scheduling.workflow.controller;

import com.kunling.scheduling.common.audit.OperationLog;
import com.kunling.scheduling.common.audit.OperationType;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import com.kunling.scheduling.workflow.order.api.OrderResponses;
import com.kunling.scheduling.workflow.order.application.OrderService;
import com.kunling.scheduling.workflow.order.application.OrderSyncResult;
import com.kunling.scheduling.workflow.order.application.OrderSyncService;
import com.kunling.scheduling.workflow.order.application.OrderTaskOrchestrationService;
import com.kunling.scheduling.workflow.order.domain.OrderStatus;
import com.kunling.scheduling.workflow.resp.TaskInfoResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "客户订单与任务")
public class OrderController extends BaseController {
    private final OrderService orderService;
    private final OrderSyncService syncService;
    private final OrderTaskOrchestrationService orchestrationService;


    public OrderController(OrderService orderService, OrderSyncService syncService,
                           OrderTaskOrchestrationService orchestrationService) {
        this.orderService = orderService;
        this.syncService = syncService;
        this.orchestrationService = orchestrationService;
    }

    @GetMapping("/orders")
    @Operation(summary = "分页查询订单")
    public ApiResult<OrderResponses.Page> page(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) OrderStatus status,
                                               @RequestParam(required = false) String source,
                                               @RequestParam(required = false) String keyword) {
        return success(orderService.page(pageNum, pageSize, status, source, keyword));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "查询订单详情")
    public ApiResult<OrderResponses.Detail> detail(@Parameter Long id) {
        return success(orderService.detail(id));
    }

    @GetMapping("/orders/{id}/tasks")
    @Operation(summary = "查询订单的全部任务")
    public ApiResult<List<OrderResponses.TaskItem>> tasks(@PathVariable Long id) {
        return success(orderService.tasks(id));
    }

    @GetMapping("/orders/{id}/task-summary")
    @Operation(summary = "查询订单任务进度和当前任务")
    public ApiResult<OrderResponses.TaskSummary> taskSummary(@PathVariable Long id) {
        return success(orderService.summary(id));
    }

    @PostMapping("/orders/sync")
    @Operation(summary = "人工触发客户订单同步")
    @OperationLog(module = "订单管理", operation = "人工同步客户订单", type = OperationType.EXECUTE,
            recordResponse = false)
    public ApiResult<OrderSyncResult> sync() {
        return success(syncService.syncAll());
    }

    @PostMapping("/order-tasks/retry")
    @Operation(summary = "重试失败任务或恢复等待任务")
    public ApiResult<Map<String, Boolean>> retry() {
        return success(Collections.singletonMap("success", orchestrationService.dispatchNext()));
    }


 /*   @GetMapping("/taskInfo")
    @Operation(summary = "查看当前订单下的流程任务信息")
    public ApiResult<TaskInfoResp> taskInfo() {
        return ApiResult.success(orchestrationService.taskInfo());
    }*/
}
