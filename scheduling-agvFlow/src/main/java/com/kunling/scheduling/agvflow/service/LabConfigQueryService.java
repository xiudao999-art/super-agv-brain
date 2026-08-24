package com.kunling.scheduling.agvflow.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.common.exception.ResourceNotFoundException;
import com.kunling.scheduling.agvflow.enums.LabConfigStatus;
import com.kunling.scheduling.agvflow.enums.LabObjectKind;
import com.kunling.scheduling.agvflow.domain.dto.LabConfigCounts;
import com.kunling.scheduling.agvflow.domain.dto.LabConfigDetail;
import com.kunling.scheduling.agvflow.domain.dto.LabConfigSummary;
import com.kunling.scheduling.agvflow.domain.dto.LabLinkView;
import com.kunling.scheduling.agvflow.domain.dto.LabMachineView;
import com.kunling.scheduling.agvflow.domain.dto.LabMapView;
import com.kunling.scheduling.agvflow.domain.dto.LabNodeView;
import com.kunling.scheduling.agvflow.domain.dto.LabPointView;
import com.kunling.scheduling.agvflow.domain.dto.LabSpaceSummary;
import com.kunling.scheduling.agvflow.domain.entity.LabConfigEntity;
import com.kunling.scheduling.agvflow.domain.entity.LabConfigLinkEntity;
import com.kunling.scheduling.agvflow.domain.entity.LabConfigObjectEntity;
import com.kunling.scheduling.agvflow.mapper.LabConfigLinkMapper;
import com.kunling.scheduling.agvflow.mapper.LabConfigMapper;
import com.kunling.scheduling.agvflow.mapper.LabConfigObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 负责把三张精简表装配为面向前端的稳定查询模型。
 */
@Service
public class LabConfigQueryService {

    private final LabConfigMapper configMapper;
    private final LabConfigObjectMapper objectMapper;
    private final LabConfigLinkMapper linkMapper;

    public LabConfigQueryService(LabConfigMapper configMapper,
                                 LabConfigObjectMapper objectMapper,
                                 LabConfigLinkMapper linkMapper) {
        this.configMapper = configMapper;
        this.objectMapper = objectMapper;
        this.linkMapper = linkMapper;
    }

    public List<LabSpaceSummary> listSpaces() {
        List<LabConfigEntity> configurations = configMapper.selectList(
                Wrappers.<LabConfigEntity>lambdaQuery()
                        .orderByAsc(LabConfigEntity::getSpaceCode)
                        .orderByDesc(LabConfigEntity::getRevision));
        Map<String, LabSpaceAccumulator> spaces = new LinkedHashMap<>();
        List<Long> activeConfigIds = new ArrayList<>();
        for (LabConfigEntity configuration : configurations) {
            LabSpaceAccumulator accumulator = spaces.computeIfAbsent(
                    configuration.getSpaceId(), ignored -> new LabSpaceAccumulator(configuration));
            accumulator.accept(configuration);
            if (LabConfigStatus.DRAFT.name().equals(configuration.getStatus())
                    || LabConfigStatus.PUBLISHED.name().equals(configuration.getStatus())) {
                activeConfigIds.add(configuration.getId());
            }
        }
        Map<Long, LabConfigCounts> countsByConfigId = loadCounts(activeConfigIds);
        List<LabSpaceSummary> result = new ArrayList<>();
        for (LabSpaceAccumulator accumulator : spaces.values()) {
            result.add(accumulator.toSummary(countsByConfigId));
        }
        return result;
    }

    public LabConfigDetail getConfig(Long configId) {
        LabConfigEntity configuration = configMapper.selectById(configId);
        if (configuration == null) {
            throw new ResourceNotFoundException("实验室配置不存在: " + configId);
        }
        List<LabConfigObjectEntity> objects = objectMapper.selectList(
                Wrappers.<LabConfigObjectEntity>lambdaQuery()
                        .eq(LabConfigObjectEntity::getConfigId, configId)
                        .orderByAsc(LabConfigObjectEntity::getId));
        List<LabConfigLinkEntity> links = linkMapper.selectList(
                Wrappers.<LabConfigLinkEntity>lambdaQuery()
                        .eq(LabConfigLinkEntity::getConfigId, configId)
                        .orderByAsc(LabConfigLinkEntity::getId));
        return new LabConfigDetail(
                configuration.getId(), configuration.getSpaceId(), configuration.getSpaceCode(),
                configuration.getSpaceName(), configuration.getRevision(), configuration.getStatus(),
                new LabMapView(configuration.getMapName(), configuration.getMapVersion(),
                        configuration.getMapFileRef()),
                objects.stream().filter(value -> LabObjectKind.TRAFFIC_NODE.name().equals(value.getKind()))
                        .map(LabConfigQueryService::toNodeView).collect(Collectors.toList()),
                objects.stream().filter(value -> LabObjectKind.MACHINE.name().equals(value.getKind()))
                        .map(LabConfigQueryService::toMachineView).collect(Collectors.toList()),
                objects.stream().filter(value -> LabObjectKind.MACHINE_POINT.name().equals(value.getKind()))
                        .map(LabConfigQueryService::toPointView).collect(Collectors.toList()),
                links.isEmpty() ? Collections.emptyList() : links.stream()
                        .map(LabConfigQueryService::toLinkView).collect(Collectors.toList()));
    }

    public LabConfigSummary toSummary(LabConfigEntity configuration) {
        LabConfigCounts counts = loadCounts(Collections.singletonList(configuration.getId()))
                .get(configuration.getId());
        return toSummary(configuration, counts);
    }

    private Map<Long, LabConfigCounts> loadCounts(List<Long> configIds) {
        if (configIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, LabConfigCounts> counts = new HashMap<>();
        for (Long configId : configIds) {
            counts.put(configId, LabConfigCounts.empty());
        }
        List<LabConfigObjectEntity> objects = objectMapper.selectList(
                Wrappers.<LabConfigObjectEntity>lambdaQuery().in(LabConfigObjectEntity::getConfigId, configIds));
        for (LabConfigObjectEntity object : objects) {
            LabConfigCounts value = counts.get(object.getConfigId());
            if (LabObjectKind.TRAFFIC_NODE.name().equals(object.getKind())) {
                value.setNodeCount(value.getNodeCount() + 1);
            } else if (LabObjectKind.MACHINE.name().equals(object.getKind())) {
                value.setMachineCount(value.getMachineCount() + 1);
            } else if (LabObjectKind.MACHINE_POINT.name().equals(object.getKind())) {
                value.setPointCount(value.getPointCount() + 1);
            }
        }
        List<LabConfigLinkEntity> links = linkMapper.selectList(
                Wrappers.<LabConfigLinkEntity>lambdaQuery().in(LabConfigLinkEntity::getConfigId, configIds));
        for (LabConfigLinkEntity link : links) {
            LabConfigCounts value = counts.get(link.getConfigId());
            value.setLinkCount(value.getLinkCount() + 1);
        }
        return counts;
    }

    private static LabNodeView toNodeView(LabConfigObjectEntity entity) {
        return new LabNodeView(entity.getId(), entity.getCode(), entity.getName(), entity.getType(),
                entity.getLocationId(), entity.getX(), entity.getY(), entity.getRz());
    }

    private static LabMachineView toMachineView(LabConfigObjectEntity entity) {
        return new LabMachineView(entity.getId(), entity.getCode(), entity.getName(), entity.getType(),
                entity.getX(), entity.getY(), entity.getRz());
    }

    private static LabPointView toPointView(LabConfigObjectEntity entity) {
        return new LabPointView(entity.getId(), entity.getParentId(), entity.getLocationId(),
                entity.getNavObjectId(), entity.getCode(), entity.getName(), entity.getType(),
                entity.getCoordinateFrame(), entity.getX(), entity.getY(), entity.getZ(),
                entity.getRx(), entity.getRy(), entity.getRz());
    }

    private static LabLinkView toLinkView(LabConfigLinkEntity entity) {
        return new LabLinkView(entity.getId(), entity.getCode(), entity.getStartObjectId(),
                entity.getEndObjectId(), entity.getDirection(), entity.getSpeedLimit());
    }

    private static LabConfigSummary toSummary(LabConfigEntity entity, LabConfigCounts counts) {
        if (entity == null) {
            return null;
        }
        return new LabConfigSummary(
                entity.getId(), entity.getRevision(), entity.getStatus(),
                new LabMapView(entity.getMapName(), entity.getMapVersion(), entity.getMapFileRef()),
                counts == null ? LabConfigCounts.empty() : counts);
    }

    private static final class LabSpaceAccumulator {
        private final String id;
        private final String code;
        private final String name;
        private LabConfigEntity published;
        private LabConfigEntity draft;

        private LabSpaceAccumulator(LabConfigEntity source) {
            this.id = source.getSpaceId();
            this.code = source.getSpaceCode();
            this.name = source.getSpaceName();
        }

        private void accept(LabConfigEntity configuration) {
            if (LabConfigStatus.DRAFT.name().equals(configuration.getStatus())) {
                draft = configuration;
            } else if (LabConfigStatus.PUBLISHED.name().equals(configuration.getStatus())) {
                published = configuration;
            }
        }

        private LabSpaceSummary toSummary(Map<Long, LabConfigCounts> countsByConfigId) {
            LabConfigCounts publishedCounts = published == null ? null : countsByConfigId.get(published.getId());
            LabConfigCounts draftCounts = draft == null ? null : countsByConfigId.get(draft.getId());
            return new LabSpaceSummary(id, code, name,
                    LabConfigQueryService.toSummary(published, publishedCounts),
                    LabConfigQueryService.toSummary(draft, draftCounts));
        }
    }
}
