package com.kunling.scheduling.action.controller;

import com.kunling.scheduling.action.exceptionmapping.application.ActionErrorMappingRuleService;
import com.kunling.scheduling.action.exceptionmapping.application.ActionErrorMappingRuleView;
import com.kunling.scheduling.action.exceptionmapping.application.SaveErrorMappingRuleRequest;
import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleStatus;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/** 动态维护厂家原始异常到平台业务异常的映射规则。 */
@Tag(name = "异常映射管理", description = "维护厂家、型号、适配器及原始码到业务异常的映射")
@RestController
@RequestMapping("/api/action-error-mappings")
public class ActionErrorMappingController extends BaseController {
    private final ActionErrorMappingRuleService ruleService;

    public ActionErrorMappingController(ActionErrorMappingRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @Operation(summary = "查询异常映射规则")
    @GetMapping
    public ApiResult<List<ActionErrorMappingRuleView>> list(
            @Parameter(description = "规则状态；不传时查询全部")
            @RequestParam(required = false) ErrorMappingRuleStatus status) {
        return success(ruleService.list(status));
    }

    @Operation(summary = "查询异常映射详情")
    @GetMapping("/{ruleId}")
    public ApiResult<ActionErrorMappingRuleView> get(@PathVariable String ruleId) {
        return success(ruleService.get(ruleId));
    }

    @Operation(summary = "新建异常映射草稿")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<ActionErrorMappingRuleView> create(
            @RequestBody SaveErrorMappingRuleRequest request) {
        requireRequest(request);
        if (request.expectedRevision() != null) {
            throw new IllegalArgumentException("新建异常映射不能携带 expectedRevision。");
        }
        return created(ruleService.create(request.rule()));
    }

    @Operation(summary = "修改异常映射草稿")
    @PutMapping("/{ruleId}")
    public ApiResult<ActionErrorMappingRuleView> update(@PathVariable String ruleId,
                                                        @RequestBody SaveErrorMappingRuleRequest request) {
        requireRequest(request);
        if (request.expectedRevision() == null) {
            throw new IllegalArgumentException("修改异常映射必须携带 expectedRevision。");
        }
        return success(ruleService.update(ruleId, request.expectedRevision(), request.rule()));
    }

    @Operation(summary = "启用异常映射")
    @PostMapping("/{ruleId}/activate")
    public ApiResult<ActionErrorMappingRuleView> activate(@PathVariable String ruleId,
                                                          @RequestParam long expectedRevision) {
        return success(ruleService.activate(ruleId, expectedRevision));
    }

    @Operation(summary = "停用异常映射")
    @PostMapping("/{ruleId}/disable")
    public ApiResult<ActionErrorMappingRuleView> disable(@PathVariable String ruleId,
                                                         @RequestParam long expectedRevision) {
        return success(ruleService.disable(ruleId, expectedRevision));
    }

    @Operation(summary = "删除异常映射", description = "ACTIVE 规则必须先停用")
    @DeleteMapping("/{ruleId}")
    public ApiResult<Void> delete(@PathVariable String ruleId,
                                  @RequestParam long expectedRevision) {
        ruleService.delete(ruleId, expectedRevision);
        return success();
    }

    private void requireRequest(SaveErrorMappingRuleRequest request) {
        if (request == null || request.rule() == null) {
            throw new IllegalArgumentException("异常映射规则请求及 rule 不能为空。");
        }
    }
}
