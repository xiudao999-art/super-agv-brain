package com.kunling.scheduling.action.interfaces.rest;

import com.kunling.scheduling.action.shared.ImmutableCollections;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.compilation.domain.ExecutionNode;
import com.kunling.scheduling.action.compilation.domain.CompileResult;
import com.kunling.scheduling.action.definition.application.ActionControlPlaneService;
import com.kunling.scheduling.action.definition.application.ActionDraftView;
import com.kunling.scheduling.action.definition.application.ActionReleaseDiff;
import com.kunling.scheduling.action.definition.application.ActionReleaseView;
import com.kunling.scheduling.action.interfaces.rest.ActionRequests.CloneReleaseRequest;
import com.kunling.scheduling.action.interfaces.rest.ActionRequests.PublishDraftRequest;
import com.kunling.scheduling.action.interfaces.rest.ActionRequests.SaveDraftRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.kunling.scheduling.action.interfaces.docs.ActionApiDocumentation.TAG_ACTION_MANAGEMENT;

@RestController
@RequestMapping("/api")
@Tag(name = TAG_ACTION_MANAGEMENT, description = "维护动作草稿、发布版本及全局组合动作目录")
public class ActionController {

    private final ActionControlPlaneService controlPlane;

    public ActionController(ActionControlPlaneService controlPlane) {
        this.controlPlane = controlPlane;
    }

    @GetMapping("/actions/drafts")
    @Operation(summary = "查询动作草稿列表", description = "返回当前全部可编辑动作草稿及其修订状态")
    @ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true)
    public List<ActionDraftView> listDrafts() {
        return controlPlane.listDrafts();
    }

    @GetMapping("/actions/drafts/{draftId}")
    @Operation(summary = "查询动作草稿详情", description = "根据草稿标识读取可编辑动作定义")
    @ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true)
    public ActionDraftView getDraft(
            @Parameter(description = "动作草稿唯一标识", required = true)
            @PathVariable UUID draftId
    ) {
        return controlPlane.getDraft(draftId);
    }

    @PostMapping("/actions/drafts")
    @Operation(summary = "保存动作草稿", description = "新建动作草稿，或携带修订号更新已有草稿")
    @ApiResponse(responseCode = "201", description = "草稿保存成功", useReturnTypeSchema = true)
    public ResponseEntity<ActionDraftView> saveDraft(@RequestBody SaveDraftRequest request) {
        ActionDraftView saved = controlPlane.saveDraft(
                request.definition(), request.draftId(), request.expectedRevision()
        );
        return ResponseEntity.created(URI.create("/api/actions/drafts/" + saved.id())).body(saved);
    }

    @DeleteMapping("/actions/drafts/{draftId}")
    @Operation(summary = "删除动作草稿", description = "按照期望修订号删除未发布草稿，防止并发覆盖")
    @ApiResponse(responseCode = "204", description = "草稿删除成功")
    public ResponseEntity<Void> deleteDraft(
            @Parameter(description = "动作草稿唯一标识", required = true) @PathVariable UUID draftId,
            @Parameter(description = "期望的草稿修订号", required = true) @RequestParam long expectedRevision
    ) {
        controlPlane.deleteDraft(draftId, expectedRevision);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/actions/drafts/clone")
    @Operation(summary = "基于发布版本创建新草稿", description = "复制指定动作发布版本，并设置一个尚未使用的新版本号")
    @ApiResponse(responseCode = "200", description = "草稿创建成功", useReturnTypeSchema = true)
    public ActionDraftView cloneRelease(@RequestBody CloneReleaseRequest request) {
        return controlPlane.cloneRelease(request.actionKey(), request.sourceVersion(), request.newVersion());
    }

    @PostMapping("/actions/drafts/{draftId}/compile")
    @Operation(summary = "编译动作草稿", description = "校验动作定义并生成可执行计划，但不会发布或执行动作")
    @ApiResponse(responseCode = "200", description = "编译完成", useReturnTypeSchema = true)
    public CompileResult compileDraft(
            @Parameter(description = "动作草稿唯一标识", required = true) @PathVariable UUID draftId
    ) {
        return controlPlane.compileDraft(draftId);
    }

    @PostMapping("/actions/drafts/{draftId}/publish")
    @Operation(summary = "发布动作草稿", description = "编译并发布不可变动作版本，后续修改需要创建新版本")
    @ApiResponse(responseCode = "200", description = "发布成功", useReturnTypeSchema = true)
    public ActionReleaseView publishDraft(
            @Parameter(description = "动作草稿唯一标识", required = true) @PathVariable UUID draftId,
            @RequestBody PublishDraftRequest request
    ) {
        return controlPlane.publishDraft(draftId, request.changeSummary());
    }

    @GetMapping("/actions/releases")
    @Operation(summary = "查询动作发布版本", description = "查询全部发布版本，也可按照动作编码筛选")
    @ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true)
    public List<ActionReleaseView> listReleases(
            @Parameter(description = "动作编码；不填写时查询全部动作")
            @RequestParam(required = false) String actionKey
    ) {
        return controlPlane.listReleases(actionKey);
    }

    @GetMapping("/actions/releases/{actionKey}/{version}")
    @Operation(summary = "查询动作发布版本详情", description = "按照动作编码和精确版本读取不可变发布资产")
    @ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true)
    public ActionReleaseView getRelease(
            @Parameter(description = "动作编码", required = true) @PathVariable String actionKey,
            @Parameter(description = "动作版本", required = true, example = "1.0.0") @PathVariable String version
    ) {
        return controlPlane.getRelease(actionKey, version);
    }

    @PostMapping("/actions/releases/{actionKey}/{version}/deprecate")
    @Operation(summary = "停用动作发布版本", description = "将指定发布版本标记为已停用，不修改其历史内容")
    @ApiResponse(responseCode = "200", description = "停用成功", useReturnTypeSchema = true)
    public ActionReleaseView deprecate(
            @Parameter(description = "动作编码", required = true) @PathVariable String actionKey,
            @Parameter(description = "动作版本", required = true, example = "1.0.0") @PathVariable String version
    ) {
        return controlPlane.deprecateRelease(actionKey, version);
    }

    @GetMapping("/actions/releases/{actionKey}/{version}/snapshot")
    @Operation(summary = "导出动作发布快照", description = "导出指定发布版本的执行计划、内容哈希和发布时间")
    @ApiResponse(responseCode = "200", description = "快照导出成功")
    public Object snapshot(
            @Parameter(description = "动作编码", required = true) @PathVariable String actionKey,
            @Parameter(description = "动作版本", required = true, example = "1.0.0") @PathVariable String version
    ) {
        ActionReleaseView release = controlPlane.getRelease(actionKey, version);
        return new ActionSnapshot("1.0", release.actionKey(), release.actionVersion(), release.planHash(),
                release.plan(), release.publishedAt());
    }

    @GetMapping("/actions/releases/{actionKey}/{version}/dependencies")
    @Operation(summary = "查询动作版本依赖", description = "返回动作发布版本精确引用的组合动作及能力依赖")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public Object dependencies(
            @Parameter(description = "动作编码", required = true) @PathVariable String actionKey,
            @Parameter(description = "动作版本", required = true, example = "1.0.0") @PathVariable String version
    ) {
        ActionReleaseView release = controlPlane.getRelease(actionKey, version);
        return new Dependencies(release.actionKey(), release.actionVersion(), release.dependencies());
    }

    @GetMapping("/actions/releases/{actionKey}/{fromVersion}/diff/{toVersion}")
    @Operation(summary = "比较动作发布版本", description = "比较同一动作两个发布版本的定义与执行计划差异")
    @ApiResponse(responseCode = "200", description = "比较完成", useReturnTypeSchema = true)
    public ActionReleaseDiff diff(
            @Parameter(description = "动作编码", required = true) @PathVariable String actionKey,
            @Parameter(description = "起始版本", required = true) @PathVariable String fromVersion,
            @Parameter(description = "目标版本", required = true) @PathVariable String toVersion
    ) {
        return controlPlane.compareReleases(actionKey, fromVersion, toVersion);
    }

    @GetMapping("/action-catalog")
    @Operation(summary = "查询全局组合动作目录", description = "仅返回已发布且不可作为调度入口的全局组合动作")
    @ApiResponse(responseCode = "200", description = "查询成功", useReturnTypeSchema = true)
    public List<ActionCatalogItem> actionCatalog(
            @Parameter(description = "项目或租户作用域；不填写时查询全部作用域")
            @RequestParam(required = false) String scope
    ) {
        return controlPlane.listReleases(null).stream()
                .filter(release -> release.status() == com.kunling.scheduling.action.definition.domain.ActionReleaseStatus.PUBLISHED)
                .filter(release -> !release.definition().entryPoint())
                .filter(release -> scope == null || scope.trim().isEmpty()
                        || release.definition().scope().equalsIgnoreCase(scope))
                .map(this::toCatalogItem)
                .collect(ImmutableCollections.toImmutableList());
    }

    private ActionCatalogItem toCatalogItem(ActionReleaseView release) {
        List<ActionCatalogItem.AtomicStep> atomicSteps = release.plan().nodes().stream()
                .map(node -> new ActionCatalogItem.AtomicStep(node.stepId(), node.displayName(),
                        node.capabilityKey(), node.capabilityContractHash(), node.groups().size()))
                .collect(ImmutableCollections.toImmutableList());
        return new ActionCatalogItem(release.actionKey(), release.actionVersion(),
                release.definition().displayName(), release.definition().description(), release.definition().scope(),
                release.definition().entryPoint(), release.definition().inputSchema(), release.definition().labels(),
                release.plan().nodes().stream().anyMatch(ExecutionNode::hasPhysicalSideEffect), atomicSteps,
                release.plan().compiledNodeCount(), release.dependencies().size(),
                release.definition().defaultPolicy().timeoutMs(), release.status(), release.publishedAt());
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class ActionSnapshot {
        String schemaVersion;
        String actionKey;
        String actionVersion;
        String planHash;
        Object plan;
        java.time.Instant publishedAt;
        @ConstructorProperties({"schemaVersion", "actionKey", "actionVersion", "planHash", "plan", "publishedAt"})
        public ActionSnapshot(
                String schemaVersion,
                String actionKey,
                String actionVersion,
                String planHash,
                Object plan,
                java.time.Instant publishedAt
        ) {
            this.schemaVersion = schemaVersion;
            this.actionKey = actionKey;
            this.actionVersion = actionVersion;
            this.planHash = planHash;
            this.plan = plan;
            this.publishedAt = publishedAt;
        }

    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class Dependencies {
        String actionKey;
        String actionVersion;
        Object dependencies;
        @ConstructorProperties({"actionKey", "actionVersion", "dependencies"})
        public Dependencies(
                String actionKey,
                String actionVersion,
                Object dependencies
        ) {
            this.actionKey = actionKey;
            this.actionVersion = actionVersion;
            this.dependencies = dependencies;
        }

    }
}
