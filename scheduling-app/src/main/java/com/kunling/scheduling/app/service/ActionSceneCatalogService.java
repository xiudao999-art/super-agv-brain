package com.kunling.scheduling.app.service;

import com.kunling.scheduling.app.domain.ActionSceneCatalogItem;
import com.kunling.scheduling.app.domain.ActionSceneCatalogOption;
import com.kunling.scheduling.app.mapper.ActionSceneCatalogRepository;
import com.kunling.scheduling.common.exception.InvalidRequestException;
import com.kunling.scheduling.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** 数据库业务场景目录的唯一查询入口；每次请求都读取当前配置。 */
@Service
public class ActionSceneCatalogService {
    private static final Pattern SCENE_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_-]{0,31}$");

    private final ActionSceneCatalogRepository repository;

    public ActionSceneCatalogService(ActionSceneCatalogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ActionSceneCatalogOption> listBusinessScenes() {
        return toImmutableOptions(repository.selectEnabledBusinessScenes());
    }

    @Transactional(readOnly = true)
    public List<ActionSceneCatalogOption> listOperations(String sceneCode) {
        String normalizedSceneCode = normalizeSceneCode(sceneCode);
        if (repository.countEnabledBusinessScene(normalizedSceneCode) == 0) {
            throw new ResourceNotFoundException("业务场景不存在或未启用：" + normalizedSceneCode);
        }
        return toImmutableOptions(repository.selectEnabledOperations(normalizedSceneCode));
    }

    private String normalizeSceneCode(String sceneCode) {
        if (sceneCode == null || sceneCode.trim().isEmpty()) {
            throw new InvalidRequestException("业务场景编码不能为空。");
        }
        String normalizedSceneCode = sceneCode.trim().toUpperCase(Locale.ROOT);
        if (!SCENE_CODE_PATTERN.matcher(normalizedSceneCode).matches()) {
            throw new InvalidRequestException("业务场景编码格式不合法。");
        }
        return normalizedSceneCode;
    }

    private List<ActionSceneCatalogOption> toImmutableOptions(List<ActionSceneCatalogItem> items) {
        Objects.requireNonNull(items, "目录查询结果不能为空");
        List<ActionSceneCatalogOption> options = new ArrayList<ActionSceneCatalogOption>(items.size());
        for (ActionSceneCatalogItem item : items) {
            options.add(new ActionSceneCatalogOption(item.getItemCode(), item.getDisplayName()));
        }
        return Collections.unmodifiableList(options);
    }
}
