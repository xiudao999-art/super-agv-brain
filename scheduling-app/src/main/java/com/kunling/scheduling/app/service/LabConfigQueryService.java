package com.kunling.scheduling.app.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.common.exception.ResourceNotFoundException;
import com.kunling.scheduling.app.enums.LabConfigStatus;
import com.kunling.scheduling.app.enums.LabObjectKind;
import com.kunling.scheduling.app.domain.dto.LabConfigCounts;
import com.kunling.scheduling.app.domain.dto.LabConfigDetail;
import com.kunling.scheduling.app.domain.dto.LabConfigSummary;
import com.kunling.scheduling.app.domain.dto.LabLinkView;
import com.kunling.scheduling.app.domain.dto.LabMachineView;
import com.kunling.scheduling.app.domain.dto.LabMapPointView;
import com.kunling.scheduling.app.domain.dto.LabMapView;
import com.kunling.scheduling.app.domain.dto.LabNodeView;
import com.kunling.scheduling.app.domain.dto.LabPointView;
import com.kunling.scheduling.app.domain.dto.LabSummary;
import com.kunling.scheduling.app.domain.entity.LabConfigEntity;
import com.kunling.scheduling.app.domain.entity.LabConfigLinkEntity;
import com.kunling.scheduling.app.domain.entity.LabConfigObjectEntity;
import com.kunling.scheduling.app.mapper.LabConfigLinkMapper;
import com.kunling.scheduling.app.mapper.LabConfigMapper;
import com.kunling.scheduling.app.mapper.LabConfigObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    private final LabMapPointProjector mapPointProjector;

    public LabConfigQueryService(LabConfigMapper configMapper,
                                 LabConfigObjectMapper objectMapper,
                                 LabConfigLinkMapper linkMapper,
                                 LabMapPointProjector mapPointProjector) {
        this.configMapper = configMapper;
        this.objectMapper = objectMapper;
        this.linkMapper = linkMapper;
        this.mapPointProjector = mapPointProjector;
    }

    public LabSummary getLab() {
        List<LabConfigEntity> configurations = configMapper.selectList(
                Wrappers.<LabConfigEntity>lambdaQuery()
                        .orderByDesc(LabConfigEntity::getRevision));
        if (configurations.isEmpty()) {
            throw new ResourceNotFoundException("实验室尚未初始化");
        }
        List<Long> activeConfigIds = new ArrayList<>();
        LabConfigEntity published = null;
        LabConfigEntity draft = null;
        for (LabConfigEntity configuration : configurations) {
            if (LabConfigStatus.DRAFT.name().equals(configuration.getStatus())) {
                draft = configuration;
                activeConfigIds.add(configuration.getId());
            } else if (LabConfigStatus.PUBLISHED.name().equals(configuration.getStatus())) {
                published = configuration;
                activeConfigIds.add(configuration.getId());
            }
        }
        Map<Long, LabConfigCounts> countsByConfigId = loadCounts(activeConfigIds);
        LabConfigCounts publishedCounts = published == null ? null : countsByConfigId.get(published.getId());
        LabConfigCounts draftCounts = draft == null ? null : countsByConfigId.get(draft.getId());
        return new LabSummary(configurations.get(0).getLabName(),
                toSummary(published, publishedCounts), toSummary(draft, draftCounts));
    }

    public LabConfigDetail getConfig(Long configId) {
        LabConfigEntity configuration = requireConfig(configId);
        List<LabConfigObjectEntity> objects = listObjects(configId);
        List<LabConfigLinkEntity> links = linkMapper.selectList(
                Wrappers.<LabConfigLinkEntity>lambdaQuery()
                        .eq(LabConfigLinkEntity::getConfigId, configId)
                        .orderByAsc(LabConfigLinkEntity::getId));
        return new LabConfigDetail(
                configuration.getId(), configuration.getLabName(),
                configuration.getRevision(), configuration.getStatus(),
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

    public List<LabMapPointView> listMapPoints(Long configId) {
        requireConfig(configId);
        return mapPointProjector.project(listObjects(configId));
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

    private LabConfigEntity requireConfig(Long configId) {
        LabConfigEntity configuration = configMapper.selectById(configId);
        if (configuration == null) {
            throw new ResourceNotFoundException("实验室配置不存在: " + configId);
        }
        return configuration;
    }

    private List<LabConfigObjectEntity> listObjects(Long configId) {
        return objectMapper.selectList(
                Wrappers.<LabConfigObjectEntity>lambdaQuery()
                        .eq(LabConfigObjectEntity::getConfigId, configId)
                        .orderByAsc(LabConfigObjectEntity::getId));
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

}
