package com.kunling.scheduling.app.controller;

import com.kunling.scheduling.app.domain.OperationLogBatchDeleteRequest;
import com.kunling.scheduling.app.domain.OperationLogBatchDeleteResult;
import com.kunling.scheduling.app.domain.OperationLogPage;
import com.kunling.scheduling.app.domain.OperationLogQueryRequest;
import com.kunling.scheduling.app.service.OperationLogService;
import com.kunling.scheduling.common.audit.OperationLog;
import com.kunling.scheduling.common.audit.OperationType;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/** 面向前端系统日志页面的查询与清理接口。 */
@RestController
@RequestMapping("/api/system-operation-logs")
@Tag(name = "系统操作日志", description = "分页筛选和批量清理重要业务操作日志")
public class OperationLogController extends BaseController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    @Operation(summary = "分页筛选操作日志")
    public ApiResult<OperationLogPage> page(@Valid OperationLogQueryRequest request) {
        return success(operationLogService.page(request.toCriteria()));
    }

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除操作日志")
    @OperationLog(module = "系统日志", operation = "批量删除操作日志", type = OperationType.DELETE,
            recordResponse = false)
    public ApiResult<OperationLogBatchDeleteResult> deleteBatch(
            @Valid @RequestBody OperationLogBatchDeleteRequest request) {
        return success(operationLogService.deleteBatch(request.getIds()));
    }
}
