package com.kunling.scheduling.app.controller;

import com.kunling.scheduling.agvflow.domain.dto.InitializeLabRequest;
import com.kunling.scheduling.agvflow.domain.dto.LabConfigVersionResult;
import com.kunling.scheduling.agvflow.domain.dto.LabSummary;
import com.kunling.scheduling.agvflow.domain.dto.UpdateLabRequest;
import com.kunling.scheduling.agvflow.service.LabConfigApplicationService;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/lab")
@Tag(name = "实验室配置", description = "维护唯一实验室、唯一逻辑地图及其配置版本")
public class LabController extends BaseController {

    private final LabConfigApplicationService applicationService;

    public LabController(LabConfigApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    @Operation(summary = "查询唯一实验室")
    public ApiResult<LabSummary> get() {
        return success(applicationService.getLab());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "初始化唯一实验室及首个配置草稿")
    public ApiResult<LabConfigVersionResult> initialize(
            @Valid @RequestBody InitializeLabRequest request) {
        return created(applicationService.initializeLab(request));
    }

    @PutMapping
    @Operation(summary = "修改实验室名称")
    public ApiResult<Void> update(@Valid @RequestBody UpdateLabRequest request) {
        applicationService.updateLabName(request);
        return success();
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "从已发布版本创建新草稿")
    public ApiResult<LabConfigVersionResult> createDraft() {
        return created(applicationService.createDraft());
    }
}
