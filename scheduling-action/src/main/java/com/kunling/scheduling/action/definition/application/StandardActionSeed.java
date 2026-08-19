package com.kunling.scheduling.action.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.infrastructure.ActionDraftRepository;
import com.kunling.scheduling.action.definition.infrastructure.ActionReleaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;

@Component
public class StandardActionSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StandardActionSeed.class);
    private final ActionDraftRepository draftRepository;
    private final ActionReleaseRepository releaseRepository;
    private final ActionControlPlaneService controlPlane;
    private final ObjectMapper objectMapper;

    public StandardActionSeed(
            ActionDraftRepository draftRepository,
            ActionReleaseRepository releaseRepository,
            ActionControlPlaneService controlPlane,
            ObjectMapper objectMapper) {
        this.draftRepository = draftRepository;
        this.releaseRepository = releaseRepository;
        this.controlPlane = controlPlane;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 只初始化全新数据库；工作人员主动删除草稿后，重启不能把数据悄悄补回来。
        if (draftRepository.count() > 0 || releaseRepository.count() > 0) {
            return;
        }
        var resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:/standard-actions/*.json");
        Arrays.sort(resources, Comparator.comparing(resource -> resource.getFilename() == null ? "" : resource.getFilename()));
        for (var resource : resources) {
            try (var input = resource.getInputStream()) {
                ActionDefinition definition = objectMapper.readValue(input, ActionDefinition.class);
                controlPlane.saveDraft(definition, null, null);
            }
        }
        log.info("已初始化 {} 个天津标准 Action 草稿", resources.length);
    }
}
