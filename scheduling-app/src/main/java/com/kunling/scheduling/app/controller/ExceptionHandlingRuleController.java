package com.kunling.scheduling.app.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kunling.scheduling.app.domain.ExceptionRuleRequests;
import com.kunling.scheduling.app.domain.ExceptionRuleResponses;
import com.kunling.scheduling.app.domain.ExceptionRuleStatus;
import com.kunling.scheduling.app.service.ExceptionHandlingRuleService;
import com.kunling.scheduling.common.audit.OperationLog;
import com.kunling.scheduling.common.audit.OperationType;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/exception-handling-rules")
@Tag(name = "异常处置规程", description = "异常处置规程增删改查及状态管理")
public class ExceptionHandlingRuleController extends BaseController {
    private final ExceptionHandlingRuleService service;

    public ExceptionHandlingRuleController(ExceptionHandlingRuleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "分页查询异常处置规程")
    public ApiResult<IPage<ExceptionRuleResponses.Summary>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) ExceptionRuleStatus status,
            @RequestParam(required = false) String keyword) {
        return success(service.page(pageNum, pageSize, status, keyword));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询异常处置规程详情")
    public ApiResult<ExceptionRuleResponses.Detail> detail(@PathVariable Long id) {
        return success(service.detail(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增异常处置规程")
    @OperationLog(module = "异常处置规程", operation = "新增规程", type = OperationType.CREATE,
            recordResponse = false)
    public ApiResult<ExceptionRuleResponses.Detail> create(
            @Valid @RequestBody ExceptionRuleRequests.Save request) {
        return created(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改异常处置规程")
    @OperationLog(module = "异常处置规程", operation = "修改规程", type = OperationType.UPDATE,
            recordResponse = false)
    public ApiResult<ExceptionRuleResponses.Detail> update(
            @PathVariable Long id, @Valid @RequestBody ExceptionRuleRequests.Save request) {
        return success(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除异常处置规程")
    @OperationLog(module = "异常处置规程", operation = "删除规程", type = OperationType.DELETE,
            recordResponse = false)
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return success();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "修改异常处置规程状态")
    @OperationLog(module = "异常处置规程", operation = "修改规程状态", type = OperationType.UPDATE,
            recordResponse = false)
    public ApiResult<ExceptionRuleResponses.Detail> changeStatus(
            @PathVariable Long id, @Valid @RequestBody ExceptionRuleRequests.ChangeStatus request) {
        return success(service.changeStatus(id, request.getStatus()));
    }
}
