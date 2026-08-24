package com.kunling.scheduling.app.controller;

import com.kunling.scheduling.agvflow.service.LabConfigApplicationService;
import com.kunling.scheduling.agvflow.domain.dto.CreatedResource;
import com.kunling.scheduling.agvflow.domain.dto.LabConfigDetail;
import com.kunling.scheduling.agvflow.domain.dto.LabLinkRequest;
import com.kunling.scheduling.agvflow.domain.dto.LabMachineRequest;
import com.kunling.scheduling.agvflow.domain.dto.LabNodeRequest;
import com.kunling.scheduling.agvflow.domain.dto.LabPointRequest;
import com.kunling.scheduling.agvflow.domain.dto.LabConfigSummary;
import com.kunling.scheduling.agvflow.domain.dto.LabMapRequest;
import com.kunling.scheduling.agvflow.domain.dto.LabMapPointView;
import com.kunling.scheduling.agvflow.domain.dto.ValidationResult;
import com.kunling.scheduling.common.web.ApiResult;
import com.kunling.scheduling.common.web.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/lab-configs")
@Tag(name = "实验室配置版本", description = "维护空间配置草稿并执行校验和发布")
public class LabConfigController extends BaseController {

    private final LabConfigApplicationService applicationService;

    public LabConfigController(LabConfigApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/{configId}")
    @Operation(summary = "查询配置详情")
    public ApiResult<LabConfigDetail> get(@PathVariable Long configId) {
        return success(applicationService.getConfig(configId));
    }

    @GetMapping("/{configId}/map-points")
    @Operation(summary = "查询地图点位列表", description = "返回统一换算为地图坐标的节点、机台锚点和机台点位")
    public ApiResult<List<LabMapPointView>> listMapPoints(@PathVariable Long configId) {
        return success(applicationService.listMapPoints(configId));
    }

    @PutMapping("/{configId}/map")
    @Operation(summary = "修改草稿地图图片信息")
    public ApiResult<Void> updateMap(@PathVariable Long configId,
                                     @Valid @RequestBody LabMapRequest request) {
        applicationService.updateMap(configId, request);
        return success();
    }

    @DeleteMapping("/{configId}")
    @Operation(summary = "删除配置草稿")
    public ApiResult<Void> deleteDraft(@PathVariable Long configId) {
        applicationService.deleteDraft(configId);
        return success();
    }

    @PostMapping("/{configId}/nodes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增通行节点")
    public ApiResult<CreatedResource> createNode(
            @PathVariable Long configId, @Valid @RequestBody LabNodeRequest request) {
        return created(applicationService.createNode(configId, request));
    }

    @PutMapping("/{configId}/nodes/{nodeId}")
    @Operation(summary = "修改通行节点")
    public ApiResult<Void> updateNode(@PathVariable Long configId,
                                      @PathVariable Long nodeId,
                                      @Valid @RequestBody LabNodeRequest request) {
        applicationService.updateNode(configId, nodeId, request);
        return success();
    }

    @DeleteMapping("/{configId}/nodes/{nodeId}")
    @Operation(summary = "删除通行节点")
    public ApiResult<Void> deleteNode(@PathVariable Long configId, @PathVariable Long nodeId) {
        applicationService.deleteNode(configId, nodeId);
        return success();
    }

    @PostMapping("/{configId}/machines")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增机台")
    public ApiResult<CreatedResource> createMachine(
            @PathVariable Long configId, @Valid @RequestBody LabMachineRequest request) {
        return created(applicationService.createMachine(configId, request));
    }

    @PutMapping("/{configId}/machines/{machineId}")
    @Operation(summary = "修改机台")
    public ApiResult<Void> updateMachine(@PathVariable Long configId,
                                         @PathVariable Long machineId,
                                         @Valid @RequestBody LabMachineRequest request) {
        applicationService.updateMachine(configId, machineId, request);
        return success();
    }

    @DeleteMapping("/{configId}/machines/{machineId}")
    @Operation(summary = "删除机台")
    public ApiResult<Void> deleteMachine(@PathVariable Long configId, @PathVariable Long machineId) {
        applicationService.deleteMachine(configId, machineId);
        return success();
    }

    @PostMapping("/{configId}/points")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增机台点位")
    public ApiResult<CreatedResource> createPoint(
            @PathVariable Long configId, @Valid @RequestBody LabPointRequest request) {
        return created(applicationService.createPoint(configId, request));
    }

    @PutMapping("/{configId}/points/{pointId}")
    @Operation(summary = "修改机台点位")
    public ApiResult<Void> updatePoint(@PathVariable Long configId,
                                       @PathVariable Long pointId,
                                       @Valid @RequestBody LabPointRequest request) {
        applicationService.updatePoint(configId, pointId, request);
        return success();
    }

    @DeleteMapping("/{configId}/points/{pointId}")
    @Operation(summary = "删除机台点位")
    public ApiResult<Void> deletePoint(@PathVariable Long configId, @PathVariable Long pointId) {
        applicationService.deletePoint(configId, pointId);
        return success();
    }

    @PostMapping("/{configId}/links")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增通行连接")
    public ApiResult<CreatedResource> createLink(
            @PathVariable Long configId, @Valid @RequestBody LabLinkRequest request) {
        return created(applicationService.createLink(configId, request));
    }

    @PutMapping("/{configId}/links/{linkId}")
    @Operation(summary = "修改通行连接")
    public ApiResult<Void> updateLink(@PathVariable Long configId,
                                      @PathVariable Long linkId,
                                      @Valid @RequestBody LabLinkRequest request) {
        applicationService.updateLink(configId, linkId, request);
        return success();
    }

    @DeleteMapping("/{configId}/links/{linkId}")
    @Operation(summary = "删除通行连接")
    public ApiResult<Void> deleteLink(@PathVariable Long configId, @PathVariable Long linkId) {
        applicationService.deleteLink(configId, linkId);
        return success();
    }

    @PostMapping("/{configId}/validate")
    @Operation(summary = "校验配置草稿")
    public ApiResult<ValidationResult> validate(@PathVariable Long configId) {
        return success(applicationService.validateConfig(configId));
    }

    @PostMapping("/{configId}/publish")
    @Operation(summary = "发布配置草稿")
    public ApiResult<LabConfigSummary> publish(@PathVariable Long configId) {
        return success(applicationService.publish(configId));
    }
}
