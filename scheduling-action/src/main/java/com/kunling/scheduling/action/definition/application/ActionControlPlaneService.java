package com.kunling.scheduling.action.definition.application;

import com.kunling.scheduling.action.shared.ImmutableCollections;

import com.kunling.scheduling.action.compilation.application.ActionCompiler;
import com.kunling.scheduling.action.compilation.domain.CompileResult;
import com.kunling.scheduling.action.compilation.domain.ExecutionPlan;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionDraftStatus;
import com.kunling.scheduling.action.definition.domain.ActionReleaseStatus;
import com.kunling.scheduling.action.definition.infrastructure.ActionDraftEntity;
import com.kunling.scheduling.action.definition.infrastructure.ActionDraftRepository;
import com.kunling.scheduling.action.definition.infrastructure.ActionReleaseEntity;
import com.kunling.scheduling.action.definition.infrastructure.ActionReleaseRepository;
import com.kunling.scheduling.action.shared.JsonCodec;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ActionControlPlaneService {

    private final ActionDraftRepository draftRepository;
    private final ActionReleaseRepository releaseRepository;
    private final ActionCompiler compiler;
    private final ActionDefinitionDiffer differ;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    @Autowired
    public ActionControlPlaneService(
            ActionDraftRepository draftRepository,
            ActionReleaseRepository releaseRepository,
            ActionCompiler compiler,
            ActionDefinitionDiffer differ,
            JsonCodec jsonCodec) {
        this(draftRepository, releaseRepository, compiler, differ, jsonCodec, Clock.systemUTC());
    }

    ActionControlPlaneService(
            ActionDraftRepository draftRepository,
            ActionReleaseRepository releaseRepository,
            ActionCompiler compiler,
            ActionDefinitionDiffer differ,
            JsonCodec jsonCodec,
            Clock clock) {
        this.draftRepository = draftRepository;
        this.releaseRepository = releaseRepository;
        this.compiler = compiler;
        this.differ = differ;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    @Transactional
    public ActionDraftView saveDraft(ActionDefinition definition, UUID draftId, Long expectedRevision) {
        requireIdentity(definition);
        Instant now = clock.instant();
        if (draftId == null) {
            if (releaseRepository.findByActionKeyAndActionVersion(definition.actionKey(), definition.version()).isPresent()) {
                throw new ActionConflictException("发布版本不可覆盖，请使用新的版本号。");
            }
            if (draftRepository.findByActionKeyAndActionVersion(definition.actionKey(), definition.version()).isPresent()) {
                throw new ActionConflictException("草稿 " + definition.actionKey() + "@" + definition.version() + " 已存在。");
            }
            ActionDraftEntity created = new ActionDraftEntity(UUID.randomUUID().toString(), definition.actionKey(),
                    definition.version(), 1, ActionDraftStatus.DRAFT, jsonCodec.write(definition), now, now);
            try {
                return toView(draftRepository.saveAndFlush(created));
            } catch (DataIntegrityViolationException exception) {
                throw new ActionConflictException("草稿 " + definition.actionKey() + "@" + definition.version() + " 已存在。");
            }
        }

        ActionDraftEntity draft = requireDraftForUpdate(draftId);
        ensureEditable(draft);
        if (expectedRevision == null || expectedRevision != draft.getRevision()) {
            throw new ActionConflictException("草稿已被其他修改更新，请刷新后重试。");
        }
        releaseRepository.findByActionKeyAndActionVersion(definition.actionKey(), definition.version())
                .ifPresent(release -> {
                    throw new ActionConflictException("发布版本不可修改，请创建新版本。");
                });
        draft.update(definition.actionKey(), definition.version(), jsonCodec.write(definition), now);
        try {
            return toView(draftRepository.saveAndFlush(draft));
        } catch (DataIntegrityViolationException exception) {
            throw new ActionConflictException("草稿 " + definition.actionKey() + "@" + definition.version() + " 已存在。");
        }
    }

    @Transactional(readOnly = true)
    public ActionDraftView getDraft(UUID draftId) {
        return toView(draftRepository.findById(draftId.toString())
                .orElseThrow(() -> new ActionNotFoundException("找不到草稿 " + draftId + "。")));
    }

    @Transactional(readOnly = true)
    public List<ActionDraftView> listDrafts() {
        return draftRepository.findAllByOrderByUpdatedAtDesc().stream().map(this::toView).collect(ImmutableCollections.toImmutableList());
    }

    @Transactional
    public void deleteDraft(UUID draftId, long expectedRevision) {
        ActionDraftEntity draft = requireDraftForUpdate(draftId);
        ensureEditable(draft);
        if (draft.getRevision() != expectedRevision) {
            throw new ActionConflictException("草稿已被其他修改更新，请刷新后重试。");
        }
        draftRepository.delete(draft);
    }

    @Transactional(readOnly = true)
    public CompileResult compileDraft(UUID draftId) {
        ActionDraftEntity draft = draftRepository.findById(draftId.toString())
                .orElseThrow(() -> new ActionNotFoundException("找不到草稿 " + draftId + "。"));
        return compiler.compile(readDefinition(draft));
    }

    @Transactional
    public ActionReleaseView publishDraft(UUID draftId, String changeSummary) {
        if (changeSummary == null || changeSummary.trim().isEmpty()) {
            throw new IllegalArgumentException("发布说明不能为空。");
        }
        if (changeSummary.length() > 1000) {
            throw new IllegalArgumentException("发布说明长度不能超过 1000 字符。");
        }
        ActionDraftEntity draft = requireDraftForUpdate(draftId);
        ensureEditable(draft);
        releaseRepository.findByActionKeyAndActionVersion(draft.getActionKey(), draft.getActionVersion())
                .ifPresent(release -> {
                    throw new ActionConflictException("发布版本不可覆盖，请创建新版本。");
                });

        ActionDefinition definition = readDefinition(draft);
        CompileResult result = compiler.compile(definition);
        if (!result.success() || result.plan() == null) {
            throw new ActionCompilationException(result.issues());
        }
        Instant now = clock.instant();
        ActionReleaseEntity release = new ActionReleaseEntity(UUID.randomUUID().toString(), definition.actionKey(),
                definition.version(), ActionReleaseStatus.PUBLISHED, result.compilerVersion(),
                jsonCodec.write(definition), jsonCodec.write(result.plan()), result.canonicalJson(), result.planHash(),
                changeSummary.trim(), now);
        try {
            releaseRepository.saveAndFlush(release);
        } catch (DataIntegrityViolationException exception) {
            throw new ActionConflictException("发布版本不可覆盖，请创建新版本。");
        }
        draft.markPublished(now);
        draftRepository.save(draft);
        return toView(release);
    }

    @Transactional
    public ActionDraftView cloneRelease(String actionKey, String sourceVersion, String newVersion) {
        ActionReleaseEntity release = requireRelease(actionKey, sourceVersion);
        ActionDefinition source = readDefinition(release);
        ActionDefinition clone = new ActionDefinition(source.schemaVersion(), source.actionKey(), newVersion,
                source.displayName(), source.description(), source.entryPoint(), source.scope(), source.inputSchema(),
                source.outputSchema(), source.steps(), source.defaultPolicy(), source.labels());
        return saveDraft(clone, null, null);
    }

    @Transactional(readOnly = true)
    public ActionReleaseView getRelease(String actionKey, String version) {
        return toView(requireRelease(actionKey, version));
    }

    @Transactional(readOnly = true)
    public List<ActionReleaseView> listReleases(String actionKey) {
        List<ActionReleaseEntity> releases = actionKey == null || actionKey.trim().isEmpty()
                ? releaseRepository.findAllByOrderByPublishedAtDesc()
                : releaseRepository.findByActionKeyOrderByPublishedAtDesc(actionKey);
        return releases.stream().map(this::toView).collect(ImmutableCollections.toImmutableList());
    }

    @Transactional
    public ActionReleaseView deprecateRelease(String actionKey, String version) {
        ActionReleaseEntity release = requireRelease(actionKey, version);
        release.deprecate(clock.instant());
        return toView(releaseRepository.save(release));
    }

    @Transactional(readOnly = true)
    public ActionReleaseDiff compareReleases(String actionKey, String fromVersion, String toVersion) {
        ActionDefinition before = readDefinition(requireRelease(actionKey, fromVersion));
        ActionDefinition after = readDefinition(requireRelease(actionKey, toVersion));
        return new ActionReleaseDiff(actionKey, fromVersion, toVersion, differ.compare(before, after));
    }

    private ActionDraftEntity requireDraftForUpdate(UUID draftId) {
        return draftRepository.findByIdForUpdate(draftId.toString())
                .orElseThrow(() -> new ActionNotFoundException("找不到草稿 " + draftId + "。"));
    }

    private ActionReleaseEntity requireRelease(String actionKey, String version) {
        return releaseRepository.findByActionKeyAndActionVersion(actionKey, version)
                .orElseThrow(() -> new ActionNotFoundException("找不到发布版本 " + actionKey + "@" + version + "。"));
    }

    private void ensureEditable(ActionDraftEntity draft) {
        if (draft.getStatus() != ActionDraftStatus.DRAFT) {
            throw new ActionConflictException("已发布草稿不可修改，请克隆为新版本。");
        }
    }

    private void requireIdentity(ActionDefinition definition) {
        if (definition == null || definition.actionKey() == null || definition.actionKey().trim().isEmpty()
                || definition.version() == null || definition.version().trim().isEmpty()) {
            throw new IllegalArgumentException("actionKey 和 version 不能为空。");
        }
    }

    private ActionDefinition readDefinition(ActionDraftEntity entity) {
        return jsonCodec.read(entity.getDefinitionJson(), ActionDefinition.class);
    }

    private ActionDefinition readDefinition(ActionReleaseEntity entity) {
        return jsonCodec.read(entity.getDefinitionJson(), ActionDefinition.class);
    }

    private ActionDraftView toView(ActionDraftEntity entity) {
        return new ActionDraftView(UUID.fromString(entity.getId()), entity.getActionKey(), entity.getRevision(),
                readDefinition(entity), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private ActionReleaseView toView(ActionReleaseEntity entity) {
        ActionDefinition definition = readDefinition(entity);
        ExecutionPlan plan = jsonCodec.read(entity.getPlanJson(), ExecutionPlan.class);
        return new ActionReleaseView(UUID.fromString(entity.getId()), entity.getActionKey(), entity.getActionVersion(),
                entity.getCompilerVersion(), definition, plan, entity.getCanonicalJson(), entity.getPlanHash(),
                plan.requiredCapabilities(), plan.dependencies(), entity.getChangeSummary(), entity.getStatus(),
                entity.getPublishedAt(), entity.getDeprecatedAt());
    }
}
