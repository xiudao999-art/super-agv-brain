package com.kunling.scheduling.workflow.controller;

import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import com.kunling.scheduling.workflow.dto.FlowTemplateCreateRequest;
import com.kunling.scheduling.workflow.service.FlowTemplateService;
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
}
