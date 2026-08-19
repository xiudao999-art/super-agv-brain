package com.kunling.scheduling.action.interfaces.rest;

import com.kunling.scheduling.action.shared.ImmutableCollections;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.compilation.domain.ExecutionNode;
import com.kunling.scheduling.action.definition.application.ActionControlPlaneService;
import com.kunling.scheduling.action.definition.application.ActionReleaseView;
import com.kunling.scheduling.action.interfaces.rest.ActionRequests.CloneReleaseRequest;
import com.kunling.scheduling.action.interfaces.rest.ActionRequests.PublishDraftRequest;
import com.kunling.scheduling.action.interfaces.rest.ActionRequests.SaveDraftRequest;
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

@RestController
@RequestMapping("/api")
public class ActionController {

    private final ActionControlPlaneService controlPlane;

    public ActionController(ActionControlPlaneService controlPlane) {
        this.controlPlane = controlPlane;
    }

    @GetMapping("/actions/drafts")
    public Object listDrafts() {
        return controlPlane.listDrafts();
    }

    @GetMapping("/actions/drafts/{draftId}")
    public Object getDraft(@PathVariable UUID draftId) {
        return controlPlane.getDraft(draftId);
    }

    @PostMapping("/actions/drafts")
    public ResponseEntity<?> saveDraft(@RequestBody SaveDraftRequest request) {
        com.kunling.scheduling.action.definition.application.ActionDraftView saved =
                controlPlane.saveDraft(request.definition(), request.draftId(), request.expectedRevision());
        return ResponseEntity.created(URI.create("/api/actions/drafts/" + saved.id())).body(saved);
    }

    @DeleteMapping("/actions/drafts/{draftId}")
    public ResponseEntity<Void> deleteDraft(@PathVariable UUID draftId, @RequestParam long expectedRevision) {
        controlPlane.deleteDraft(draftId, expectedRevision);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/actions/drafts/clone")
    public Object cloneRelease(@RequestBody CloneReleaseRequest request) {
        return controlPlane.cloneRelease(request.actionKey(), request.sourceVersion(), request.newVersion());
    }

    @PostMapping("/actions/drafts/{draftId}/compile")
    public Object compileDraft(@PathVariable UUID draftId) {
        return controlPlane.compileDraft(draftId);
    }

    @PostMapping("/actions/drafts/{draftId}/publish")
    public Object publishDraft(@PathVariable UUID draftId, @RequestBody PublishDraftRequest request) {
        return controlPlane.publishDraft(draftId, request.changeSummary());
    }

    @GetMapping("/actions/releases")
    public Object listReleases(@RequestParam(required = false) String actionKey) {
        return controlPlane.listReleases(actionKey);
    }

    @GetMapping("/actions/releases/{actionKey}/{version}")
    public Object getRelease(@PathVariable String actionKey, @PathVariable String version) {
        return controlPlane.getRelease(actionKey, version);
    }

    @PostMapping("/actions/releases/{actionKey}/{version}/deprecate")
    public Object deprecate(@PathVariable String actionKey, @PathVariable String version) {
        return controlPlane.deprecateRelease(actionKey, version);
    }

    @GetMapping("/actions/releases/{actionKey}/{version}/snapshot")
    public Object snapshot(@PathVariable String actionKey, @PathVariable String version) {
        ActionReleaseView release = controlPlane.getRelease(actionKey, version);
        return new ActionSnapshot("1.0", release.actionKey(), release.actionVersion(), release.planHash(),
                release.plan(), release.publishedAt());
    }

    @GetMapping("/actions/releases/{actionKey}/{version}/dependencies")
    public Object dependencies(@PathVariable String actionKey, @PathVariable String version) {
        ActionReleaseView release = controlPlane.getRelease(actionKey, version);
        return new Dependencies(release.actionKey(), release.actionVersion(), release.dependencies());
    }

    @GetMapping("/actions/releases/{actionKey}/{fromVersion}/diff/{toVersion}")
    public Object diff(@PathVariable String actionKey, @PathVariable String fromVersion,
                       @PathVariable String toVersion) {
        return controlPlane.compareReleases(actionKey, fromVersion, toVersion);
    }

    @GetMapping("/action-catalog")
    public List<ActionCatalogItem> actionCatalog(@RequestParam(required = false) String scope) {
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
