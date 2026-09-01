package com.kunling.scheduling.app.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.common.exception.ConflictException;
import com.kunling.scheduling.common.exception.ResourceNotFoundException;
import com.kunling.scheduling.app.enums.LabConfigStatus;
import com.kunling.scheduling.app.enums.LabObjectKind;
import com.kunling.scheduling.app.domain.dto.InitializeLabRequest;
import com.kunling.scheduling.app.domain.dto.CreatedResource;
import com.kunling.scheduling.app.domain.dto.LabConfigDetail;
import com.kunling.scheduling.app.domain.dto.LabConfigSummary;
import com.kunling.scheduling.app.domain.dto.LabLinkRequest;
import com.kunling.scheduling.app.domain.dto.LabMachineRequest;
import com.kunling.scheduling.app.domain.dto.LabMapRequest;
import com.kunling.scheduling.app.domain.dto.LabMapPointView;
import com.kunling.scheduling.app.domain.dto.LabNodeRequest;
import com.kunling.scheduling.app.domain.dto.LabPointRequest;
import com.kunling.scheduling.app.domain.dto.LabConfigVersionResult;
import com.kunling.scheduling.app.domain.dto.LabSummary;
import com.kunling.scheduling.app.domain.dto.UpdateLabRequest;
import com.kunling.scheduling.app.domain.dto.ValidationResult;
import com.kunling.scheduling.app.domain.entity.LabConfigEntity;
import com.kunling.scheduling.app.domain.entity.LabConfigLinkEntity;
import com.kunling.scheduling.app.domain.entity.LabConfigObjectEntity;
import com.kunling.scheduling.app.mapper.LabConfigLinkMapper;
import com.kunling.scheduling.app.mapper.LabConfigMapper;
import com.kunling.scheduling.app.mapper.LabConfigObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实验室配置应用门面：编排唯一实验室的版本生命周期，并把查询和草稿编辑委托给独立服务。
 */
@Service
public class LabConfigApplicationService {

    private final LabConfigMapper configMapper;
    private final LabConfigObjectMapper objectMapper;
    private final LabConfigLinkMapper linkMapper;
    private final LabConfigurationValidator validator;
    private final LabConfigDraftEditor draftEditor;
    private final LabConfigQueryService queryService;

    public LabConfigApplicationService(LabConfigMapper configMapper,
                                       LabConfigObjectMapper objectMapper,
                                       LabConfigLinkMapper linkMapper,
                                       LabConfigurationValidator validator,
                                       LabConfigDraftEditor draftEditor,
                                       LabConfigQueryService queryService) {
        this.configMapper = configMapper;
        this.objectMapper = objectMapper;
        this.linkMapper = linkMapper;
        this.validator = validator;
        this.draftEditor = draftEditor;
        this.queryService = queryService;
    }

    @Transactional
    public LabConfigVersionResult initializeLab(InitializeLabRequest request) {
        if (configMapper.selectCount(Wrappers.<LabConfigEntity>lambdaQuery()) > 0) {
            throw new ConflictException("实验室已初始化，不能重复创建");
        }
        LabConfigEntity entity = new LabConfigEntity();
        entity.setLabName(request.getName().trim());
        entity.setMapName(request.getMap().getName().trim());
        entity.setMapVersion(request.getMap().getVersion().trim());
        entity.setMapFileRef(request.getMap().getImageUrl().trim());
        entity.setRevision(1);
        entity.setStatus(LabConfigStatus.DRAFT.name());
        try {
            configMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("实验室已初始化，不能重复创建", exception);
        }
        return new LabConfigVersionResult(entity.getId(), entity.getRevision(), entity.getStatus());
    }

    public LabSummary getLab() {
        return queryService.getLab();
    }

    public LabConfigDetail getConfig(Long configId) {
        return queryService.getConfig(configId);
    }

    public List<LabMapPointView> listMapPoints(Long configId) {
        return queryService.listMapPoints(configId);
    }

    @Transactional
    public void updateLabName(UpdateLabRequest request) {
        List<LabConfigEntity> configurations = configMapper.selectAllForUpdate();
        if (configurations.isEmpty()) {
            throw new ResourceNotFoundException("实验室尚未初始化");
        }
        String normalizedName = request.getName().trim();
        // 实验室名称随版本保存，重命名时同步全部版本，保证历史配置展示一致。
        for (LabConfigEntity configuration : configurations) {
            configuration.setLabName(normalizedName);
            configuration.setUpdatedAt(LocalDateTime.now());
            configMapper.updateById(configuration);
        }
    }

    @Transactional
    public void updateMap(Long configId, LabMapRequest request) {
        LabConfigEntity configuration = requireDraft(configId);
        configuration.setMapName(request.getName().trim());
        configuration.setMapVersion(request.getVersion().trim());
        configuration.setMapFileRef(request.getImageUrl().trim());
        configuration.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(configuration);
    }

    @Transactional
    public void deleteDraft(Long configId) {
        requireDraft(configId);
        linkMapper.delete(Wrappers.<LabConfigLinkEntity>lambdaQuery()
                .eq(LabConfigLinkEntity::getConfigId, configId));
        // 统一对象表存在机台和导航节点自引用，必须先删除叶子点位，再删除其父对象。
        objectMapper.delete(Wrappers.<LabConfigObjectEntity>lambdaQuery()
                .eq(LabConfigObjectEntity::getConfigId, configId)
                .eq(LabConfigObjectEntity::getKind, LabObjectKind.MACHINE_POINT.name()));
        objectMapper.delete(Wrappers.<LabConfigObjectEntity>lambdaQuery()
                .eq(LabConfigObjectEntity::getConfigId, configId));
        configMapper.deleteById(configId);
    }

    public CreatedResource createNode(Long configId, LabNodeRequest request) {
        return draftEditor.createNode(configId, request);
    }

    public void updateNode(Long configId, Long nodeId, LabNodeRequest request) {
        draftEditor.updateNode(configId, nodeId, request);
    }

    public void deleteNode(Long configId, Long nodeId) {
        draftEditor.deleteNode(configId, nodeId);
    }

    public CreatedResource createMachine(Long configId, LabMachineRequest request) {
        return draftEditor.createMachine(configId, request);
    }

    public void updateMachine(Long configId, Long machineId, LabMachineRequest request) {
        draftEditor.updateMachine(configId, machineId, request);
    }

    public void deleteMachine(Long configId, Long machineId) {
        draftEditor.deleteMachine(configId, machineId);
    }

    public CreatedResource createPoint(Long configId, LabPointRequest request) {
        return draftEditor.createPoint(configId, request);
    }

    public void updatePoint(Long configId, Long pointId, LabPointRequest request) {
        draftEditor.updatePoint(configId, pointId, request);
    }

    public void deletePoint(Long configId, Long pointId) {
        draftEditor.deletePoint(configId, pointId);
    }

    public CreatedResource createLink(Long configId, LabLinkRequest request) {
        return draftEditor.createLink(configId, request);
    }

    public void updateLink(Long configId, Long linkId, LabLinkRequest request) {
        draftEditor.updateLink(configId, linkId, request);
    }

    public void deleteLink(Long configId, Long linkId) {
        draftEditor.deleteLink(configId, linkId);
    }

    public ValidationResult validateConfig(Long configId) {
        LabConfigEntity configuration = requireConfig(configId);
        return validator.validate(configuration, listObjects(configId), listLinks(configId));
    }

    @Transactional
    public LabConfigSummary publish(Long configId) {
        List<LabConfigEntity> locked = configMapper.selectAllForUpdate();
        LabConfigEntity draft = locked.stream()
                .filter(value -> configId.equals(value.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("实验室配置不存在: " + configId));
        if (!LabConfigStatus.DRAFT.name().equals(draft.getStatus())) {
            throw new ConflictException("只有草稿配置允许发布: " + configId);
        }
        ValidationResult validation = validator.validate(draft, listObjects(configId), listLinks(configId));
        if (!validation.isValid()) {
            throw new ConflictException("配置校验未通过，不能发布");
        }

        // 先归档旧版本，再发布新版本，避免唯一实验室出现两个同时生效的配置。
        for (LabConfigEntity configuration : locked) {
            if (LabConfigStatus.PUBLISHED.name().equals(configuration.getStatus())) {
                configuration.setStatus(LabConfigStatus.ARCHIVED.name());
                configMapper.updateById(configuration);
            }
        }
        draft.setStatus(LabConfigStatus.PUBLISHED.name());
        draft.setPublishedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(draft);
        return queryService.toSummary(draft);
    }

    @Transactional
    public LabConfigVersionResult createDraft() {
        List<LabConfigEntity> locked = configMapper.selectAllForUpdate();
        if (locked.isEmpty()) {
            throw new ResourceNotFoundException("实验室尚未初始化");
        }
        if (locked.stream().anyMatch(value -> LabConfigStatus.DRAFT.name().equals(value.getStatus()))) {
            throw new ConflictException("实验室已存在草稿");
        }
        LabConfigEntity source = locked.stream()
                .filter(value -> LabConfigStatus.PUBLISHED.name().equals(value.getStatus()))
                .findFirst()
                .orElseGet(() -> locked.stream().max(Comparator.comparing(LabConfigEntity::getRevision)).get());
        int nextRevision = locked.stream().map(LabConfigEntity::getRevision).max(Integer::compareTo).get() + 1;
        LabConfigEntity draft = copyConfiguration(source, nextRevision);
        try {
            configMapper.insert(draft);
        } catch (DuplicateKeyException exception) {
            // 数据库唯一约束是并发竞争的最终防线，对外统一呈现为草稿冲突。
            throw new ConflictException("实验室已存在草稿或版本号冲突", exception);
        }
        cloneGraph(source.getId(), draft.getId());
        return new LabConfigVersionResult(draft.getId(), draft.getRevision(), draft.getStatus());
    }

    private LabConfigEntity copyConfiguration(LabConfigEntity source, int revision) {
        LabConfigEntity target = new LabConfigEntity();
        target.setLabName(source.getLabName());
        target.setMapName(source.getMapName());
        target.setMapVersion(source.getMapVersion());
        target.setMapFileRef(source.getMapFileRef());
        target.setRevision(revision);
        target.setStatus(LabConfigStatus.DRAFT.name());
        return target;
    }

    private void cloneGraph(Long sourceConfigId, Long targetConfigId) {
        List<LabConfigObjectEntity> sourceObjects = listObjects(sourceConfigId);
        Map<Long, Long> clonedIds = new HashMap<>();
        Map<Long, LabConfigObjectEntity> clonesBySourceId = new HashMap<>();
        // 第一遍先建立所有新主键；第二遍再重连自引用，避免复制出跨版本引用。
        for (LabConfigObjectEntity source : sourceObjects) {
            LabConfigObjectEntity clone = copyObject(source, targetConfigId);
            objectMapper.insert(clone);
            clonedIds.put(source.getId(), clone.getId());
            clonesBySourceId.put(source.getId(), clone);
        }
        for (LabConfigObjectEntity source : sourceObjects) {
            if (source.getParentId() == null && source.getNavObjectId() == null) {
                continue;
            }
            LabConfigObjectEntity clone = clonesBySourceId.get(source.getId());
            clone.setParentId(remapNullable(clonedIds, source.getParentId()));
            clone.setNavObjectId(remapNullable(clonedIds, source.getNavObjectId()));
            objectMapper.updateById(clone);
        }
        for (LabConfigLinkEntity source : listLinks(sourceConfigId)) {
            LabConfigLinkEntity clone = new LabConfigLinkEntity();
            clone.setConfigId(targetConfigId);
            clone.setCode(source.getCode());
            clone.setStartObjectId(requireRemapped(clonedIds, source.getStartObjectId()));
            clone.setEndObjectId(requireRemapped(clonedIds, source.getEndObjectId()));
            clone.setDirection(source.getDirection());
            clone.setSpeedLimit(source.getSpeedLimit());
            linkMapper.insert(clone);
        }
    }

    private LabConfigObjectEntity copyObject(LabConfigObjectEntity source, Long targetConfigId) {
        LabConfigObjectEntity clone = new LabConfigObjectEntity();
        clone.setConfigId(targetConfigId);
        clone.setLocationId(source.getLocationId());
        clone.setCode(source.getCode());
        clone.setName(source.getName());
        clone.setKind(source.getKind());
        clone.setType(source.getType());
        clone.setCoordinateFrame(source.getCoordinateFrame());
        clone.setX(source.getX());
        clone.setY(source.getY());
        clone.setZ(source.getZ());
        clone.setRx(source.getRx());
        clone.setRy(source.getRy());
        clone.setRz(source.getRz());
        return clone;
    }

    private Long remapNullable(Map<Long, Long> clonedIds, Long sourceId) {
        return sourceId == null ? null : requireRemapped(clonedIds, sourceId);
    }

    private Long requireRemapped(Map<Long, Long> clonedIds, Long sourceId) {
        Long targetId = clonedIds.get(sourceId);
        if (targetId == null) {
            throw new ConflictException("复制草稿时发现跨配置引用: " + sourceId);
        }
        return targetId;
    }

    private List<LabConfigObjectEntity> listObjects(Long configId) {
        return objectMapper.selectList(Wrappers.<LabConfigObjectEntity>lambdaQuery()
                .eq(LabConfigObjectEntity::getConfigId, configId)
                .orderByAsc(LabConfigObjectEntity::getId));
    }

    private List<LabConfigLinkEntity> listLinks(Long configId) {
        return linkMapper.selectList(Wrappers.<LabConfigLinkEntity>lambdaQuery()
                .eq(LabConfigLinkEntity::getConfigId, configId)
                .orderByAsc(LabConfigLinkEntity::getId));
    }

    private LabConfigEntity requireConfig(Long configId) {
        LabConfigEntity configuration = configMapper.selectById(configId);
        if (configuration == null) {
            throw new ResourceNotFoundException("实验室配置不存在: " + configId);
        }
        return configuration;
    }

    private LabConfigEntity requireDraft(Long configId) {
        LabConfigEntity configuration = requireConfig(configId);
        if (!LabConfigStatus.DRAFT.name().equals(configuration.getStatus())) {
            throw new ConflictException("只有草稿配置允许修改: " + configId);
        }
        return configuration;
    }
}
