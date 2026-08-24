package com.kunling.scheduling.agvflow.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kunling.scheduling.agvflow.enums.CoordinateFrame;
import com.kunling.scheduling.common.exception.ConflictException;
import com.kunling.scheduling.common.exception.InvalidRequestException;
import com.kunling.scheduling.common.exception.ResourceNotFoundException;
import com.kunling.scheduling.agvflow.enums.LabConfigStatus;
import com.kunling.scheduling.agvflow.enums.LabObjectKind;
import com.kunling.scheduling.agvflow.domain.dto.CreatedResource;
import com.kunling.scheduling.agvflow.domain.dto.LabLinkRequest;
import com.kunling.scheduling.agvflow.domain.dto.LabMachineRequest;
import com.kunling.scheduling.agvflow.domain.dto.LabNodeRequest;
import com.kunling.scheduling.agvflow.domain.dto.LabPointRequest;
import com.kunling.scheduling.agvflow.domain.entity.LabConfigEntity;
import com.kunling.scheduling.agvflow.domain.entity.LabConfigLinkEntity;
import com.kunling.scheduling.agvflow.domain.entity.LabConfigObjectEntity;
import com.kunling.scheduling.agvflow.mapper.LabConfigLinkMapper;
import com.kunling.scheduling.agvflow.mapper.LabConfigMapper;
import com.kunling.scheduling.agvflow.mapper.LabConfigObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 只负责草稿图对象的新增、修改和删除，版本发布逻辑由生命周期服务统一处理。
 */
@Service
public class LabConfigDraftEditor {

    private final LabConfigMapper configMapper;
    private final LabConfigObjectMapper objectMapper;
    private final LabConfigLinkMapper linkMapper;
    private final LabLocationReferenceChecker locationReferenceChecker;

    public LabConfigDraftEditor(LabConfigMapper configMapper,
                                LabConfigObjectMapper objectMapper,
                                LabConfigLinkMapper linkMapper,
                                LabLocationReferenceChecker locationReferenceChecker) {
        this.configMapper = configMapper;
        this.objectMapper = objectMapper;
        this.linkMapper = linkMapper;
        this.locationReferenceChecker = locationReferenceChecker;
    }

    @Transactional
    public CreatedResource createNode(Long configId, LabNodeRequest request) {
        LabConfigEntity configuration = requireDraft(configId);
        locationReferenceChecker.requireExisting(request.getLocationId());
        LabConfigObjectEntity entity = new LabConfigObjectEntity();
        entity.setConfigId(configId);
        entity.setKind(LabObjectKind.TRAFFIC_NODE.name());
        applyNode(entity, request);
        insertObject(entity, "节点编码已存在: " + entity.getCode());
        touch(configuration);
        return new CreatedResource(entity.getId());
    }

    @Transactional
    public void updateNode(Long configId, Long nodeId, LabNodeRequest request) {
        LabConfigEntity configuration = requireDraft(configId);
        locationReferenceChecker.requireExisting(request.getLocationId());
        LabConfigObjectEntity entity = requireObject(configId, nodeId, LabObjectKind.TRAFFIC_NODE, "通行节点");
        applyNode(entity, request);
        updateObject(entity, "节点编码已存在: " + entity.getCode());
        touch(configuration);
    }

    @Transactional
    public void deleteNode(Long configId, Long nodeId) {
        LabConfigEntity configuration = requireDraft(configId);
        requireObject(configId, nodeId, LabObjectKind.TRAFFIC_NODE, "通行节点");
        Long linkReferences = linkMapper.selectCount(Wrappers.<LabConfigLinkEntity>lambdaQuery()
                .eq(LabConfigLinkEntity::getConfigId, configId)
                .and(query -> query.eq(LabConfigLinkEntity::getStartObjectId, nodeId)
                        .or().eq(LabConfigLinkEntity::getEndObjectId, nodeId)));
        Long pointReferences = objectMapper.selectCount(Wrappers.<LabConfigObjectEntity>lambdaQuery()
                .eq(LabConfigObjectEntity::getConfigId, configId)
                .eq(LabConfigObjectEntity::getNavObjectId, nodeId));
        if (linkReferences > 0 || pointReferences > 0) {
            throw new ConflictException("通行节点仍被连接或机台点位引用: " + nodeId);
        }
        objectMapper.deleteById(nodeId);
        touch(configuration);
    }

    @Transactional
    public CreatedResource createMachine(Long configId, LabMachineRequest request) {
        LabConfigEntity configuration = requireDraft(configId);
        LabConfigObjectEntity entity = new LabConfigObjectEntity();
        entity.setConfigId(configId);
        entity.setKind(LabObjectKind.MACHINE.name());
        applyMachine(entity, request);
        insertObject(entity, "机台编码已存在: " + entity.getCode());
        touch(configuration);
        return new CreatedResource(entity.getId());
    }

    @Transactional
    public void updateMachine(Long configId, Long machineId, LabMachineRequest request) {
        LabConfigEntity configuration = requireDraft(configId);
        LabConfigObjectEntity entity = requireObject(configId, machineId, LabObjectKind.MACHINE, "机台");
        applyMachine(entity, request);
        updateObject(entity, "机台编码已存在: " + entity.getCode());
        touch(configuration);
    }

    @Transactional
    public void deleteMachine(Long configId, Long machineId) {
        LabConfigEntity configuration = requireDraft(configId);
        requireObject(configId, machineId, LabObjectKind.MACHINE, "机台");
        Long pointReferences = objectMapper.selectCount(Wrappers.<LabConfigObjectEntity>lambdaQuery()
                .eq(LabConfigObjectEntity::getConfigId, configId)
                .eq(LabConfigObjectEntity::getParentId, machineId));
        if (pointReferences > 0) {
            throw new ConflictException("机台仍被点位引用: " + machineId);
        }
        objectMapper.deleteById(machineId);
        touch(configuration);
    }

    @Transactional
    public CreatedResource createPoint(Long configId, LabPointRequest request) {
        LabConfigEntity configuration = requireDraft(configId);
        locationReferenceChecker.requireExisting(request.getLocationId());
        requireObject(configId, request.getMachineId(), LabObjectKind.MACHINE, "所属机台");
        if (request.getNavNodeId() != null) {
            requireObject(configId, request.getNavNodeId(), LabObjectKind.TRAFFIC_NODE, "关联导航节点");
        }
        LabConfigObjectEntity entity = new LabConfigObjectEntity();
        entity.setConfigId(configId);
        entity.setKind(LabObjectKind.MACHINE_POINT.name());
        applyPoint(entity, request);
        insertObject(entity, "点位编码已存在: " + entity.getCode());
        touch(configuration);
        return new CreatedResource(entity.getId());
    }

    @Transactional
    public void updatePoint(Long configId, Long pointId, LabPointRequest request) {
        LabConfigEntity configuration = requireDraft(configId);
        locationReferenceChecker.requireExisting(request.getLocationId());
        requireObject(configId, request.getMachineId(), LabObjectKind.MACHINE, "所属机台");
        if (request.getNavNodeId() != null) {
            requireObject(configId, request.getNavNodeId(), LabObjectKind.TRAFFIC_NODE, "关联导航节点");
        }
        LabConfigObjectEntity entity = requireObject(configId, pointId, LabObjectKind.MACHINE_POINT, "机台点位");
        applyPoint(entity, request);
        updateObject(entity, "点位编码已存在: " + entity.getCode());
        touch(configuration);
    }

    @Transactional
    public void deletePoint(Long configId, Long pointId) {
        LabConfigEntity configuration = requireDraft(configId);
        requireObject(configId, pointId, LabObjectKind.MACHINE_POINT, "机台点位");
        objectMapper.deleteById(pointId);
        touch(configuration);
    }

    @Transactional
    public CreatedResource createLink(Long configId, LabLinkRequest request) {
        LabConfigEntity configuration = requireDraft(configId);
        validateLinkReferences(configId, request);
        LabConfigLinkEntity entity = new LabConfigLinkEntity();
        entity.setConfigId(configId);
        applyLink(entity, request);
        insertLink(entity, "连接编码已存在: " + entity.getCode());
        touch(configuration);
        return new CreatedResource(entity.getId());
    }

    @Transactional
    public void updateLink(Long configId, Long linkId, LabLinkRequest request) {
        LabConfigEntity configuration = requireDraft(configId);
        validateLinkReferences(configId, request);
        LabConfigLinkEntity entity = requireLink(configId, linkId);
        applyLink(entity, request);
        try {
            linkMapper.updateById(entity);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("连接编码已存在: " + entity.getCode(), exception);
        }
        touch(configuration);
    }

    @Transactional
    public void deleteLink(Long configId, Long linkId) {
        LabConfigEntity configuration = requireDraft(configId);
        requireLink(configId, linkId);
        linkMapper.deleteById(linkId);
        touch(configuration);
    }

    private LabConfigEntity requireDraft(Long configId) {
        LabConfigEntity configuration = configMapper.selectById(configId);
        if (configuration == null) {
            throw new ResourceNotFoundException("实验室配置不存在: " + configId);
        }
        if (!LabConfigStatus.DRAFT.name().equals(configuration.getStatus())) {
            throw new ConflictException("只有草稿配置允许修改: " + configId);
        }
        return configuration;
    }

    private LabConfigObjectEntity requireObject(Long configId, Long objectId,
                                                LabObjectKind expectedKind, String label) {
        LabConfigObjectEntity object = objectMapper.selectById(objectId);
        if (object == null) {
            throw new ResourceNotFoundException(label + "不存在: " + objectId);
        }
        if (!configId.equals(object.getConfigId())) {
            throw new ConflictException(label + "不属于当前配置: " + objectId);
        }
        if (!expectedKind.name().equals(object.getKind())) {
            throw new InvalidRequestException(label + "类型不正确: " + objectId);
        }
        return object;
    }

    private LabConfigLinkEntity requireLink(Long configId, Long linkId) {
        LabConfigLinkEntity link = linkMapper.selectById(linkId);
        if (link == null) {
            throw new ResourceNotFoundException("通行连接不存在: " + linkId);
        }
        if (!configId.equals(link.getConfigId())) {
            throw new ConflictException("通行连接不属于当前配置: " + linkId);
        }
        return link;
    }

    private void validateLinkReferences(Long configId, LabLinkRequest request) {
        if (request.getStartNodeId().equals(request.getEndNodeId())) {
            throw new InvalidRequestException("连接起点和终点不能相同");
        }
        requireObject(configId, request.getStartNodeId(), LabObjectKind.TRAFFIC_NODE, "连接起点");
        requireObject(configId, request.getEndNodeId(), LabObjectKind.TRAFFIC_NODE, "连接终点");
    }

    private void applyNode(LabConfigObjectEntity entity, LabNodeRequest request) {
        entity.setLocationId(request.getLocationId());
        entity.setCode(request.getCode().trim());
        entity.setName(request.getName().trim());
        entity.setType(request.getType().trim());
        entity.setCoordinateFrame(CoordinateFrame.MAP.name());
        entity.setX(request.getX());
        entity.setY(request.getY());
        entity.setZ(null);
        entity.setRx(null);
        entity.setRy(null);
        entity.setRz(request.getYaw());
    }

    private void applyMachine(LabConfigObjectEntity entity, LabMachineRequest request) {
        entity.setCode(request.getCode().trim());
        entity.setName(request.getName().trim());
        entity.setType(request.getType().trim());
        entity.setCoordinateFrame(CoordinateFrame.MAP.name());
        entity.setX(request.getAnchorX());
        entity.setY(request.getAnchorY());
        entity.setZ(null);
        entity.setRx(null);
        entity.setRy(null);
        entity.setRz(request.getAnchorYaw());
    }

    private void applyPoint(LabConfigObjectEntity entity, LabPointRequest request) {
        entity.setParentId(request.getMachineId());
        entity.setLocationId(request.getLocationId());
        entity.setNavObjectId(request.getNavNodeId());
        entity.setCode(request.getCode().trim());
        entity.setName(request.getName().trim());
        entity.setType(request.getType().trim());
        entity.setCoordinateFrame(request.getFrame());
        entity.setX(request.getX());
        entity.setY(request.getY());
        entity.setZ(request.getZ());
        entity.setRx(request.getRx());
        entity.setRy(request.getRy());
        entity.setRz(request.getRz());
    }

    private void applyLink(LabConfigLinkEntity entity, LabLinkRequest request) {
        entity.setCode(request.getCode().trim());
        entity.setStartObjectId(request.getStartNodeId());
        entity.setEndObjectId(request.getEndNodeId());
        entity.setDirection(request.getDirection());
        entity.setSpeedLimit(request.getSpeedLimit());
    }

    private void insertObject(LabConfigObjectEntity entity, String duplicateMessage) {
        try {
            objectMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException(duplicateMessage, exception);
        }
    }

    private void updateObject(LabConfigObjectEntity entity, String duplicateMessage) {
        try {
            objectMapper.updateById(entity);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException(duplicateMessage, exception);
        }
    }

    private void insertLink(LabConfigLinkEntity entity, String duplicateMessage) {
        try {
            linkMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException(duplicateMessage, exception);
        }
    }

    private void touch(LabConfigEntity configuration) {
        configuration.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(configuration);
    }
}
