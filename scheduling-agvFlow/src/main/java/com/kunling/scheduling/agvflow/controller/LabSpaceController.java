package com.kunling.scheduling.agvflow.controller;

import com.kunling.scheduling.agvflow.service.LabConfigApplicationService;
import com.kunling.scheduling.agvflow.domain.dto.CreateLabSpaceRequest;
import com.kunling.scheduling.agvflow.domain.dto.CreateLabSpaceResult;
import com.kunling.scheduling.agvflow.domain.dto.LabSpaceSummary;
import com.kunling.scheduling.agvflow.domain.dto.UpdateLabSpaceRequest;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/lab-spaces")
@Tag(name = "实验室空间配置", description = "维护实验室空间及其草稿和发布版本")
public class LabSpaceController extends BaseController {

    private final LabConfigApplicationService applicationService;

    public LabSpaceController(LabConfigApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    @Operation(summary = "查询实验室空间列表")
    public ResponseEntity<ApiResult<List<LabSpaceSummary>>> list() {
        return success(applicationService.listSpaces());
    }

    @PostMapping
    @Operation(summary = "新增空间并创建首个配置草稿")
    public ResponseEntity<ApiResult<CreateLabSpaceResult>> create(
            @Valid @RequestBody CreateLabSpaceRequest request) {
        return created(applicationService.createSpace(request));
    }

    @PutMapping("/{spaceId}")
    @Operation(summary = "修改空间名称")
    public ResponseEntity<ApiResult<Void>> update(@PathVariable String spaceId,
                                                  @Valid @RequestBody UpdateLabSpaceRequest request) {
        applicationService.updateSpaceName(spaceId, request);
        return success();
    }

    @PostMapping("/{spaceId}/drafts")
    @Operation(summary = "从已发布版本创建新草稿")
    public ResponseEntity<ApiResult<CreateLabSpaceResult>> createDraft(@PathVariable String spaceId) {
        return created(applicationService.createDraft(spaceId));
    }
}
