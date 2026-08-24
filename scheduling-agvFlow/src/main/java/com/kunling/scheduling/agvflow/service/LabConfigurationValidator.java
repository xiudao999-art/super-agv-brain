package com.kunling.scheduling.agvflow.service;

import com.kunling.scheduling.agvflow.enums.CoordinateFrame;
import com.kunling.scheduling.agvflow.enums.LabLinkDirection;
import com.kunling.scheduling.agvflow.enums.LabObjectKind;
import com.kunling.scheduling.agvflow.domain.dto.ValidationIssue;
import com.kunling.scheduling.agvflow.domain.dto.ValidationResult;
import com.kunling.scheduling.agvflow.domain.entity.LabConfigEntity;
import com.kunling.scheduling.agvflow.domain.entity.LabConfigLinkEntity;
import com.kunling.scheduling.agvflow.domain.entity.LabConfigObjectEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 发布校验集中在一个模块内，Controller 和发布流程不重复拼接判断规则。
 */
@Component
public class LabConfigurationValidator {

    private static final BigDecimal MIN_ANGLE = new BigDecimal("-180");
    private static final BigDecimal MAX_ANGLE = new BigDecimal("180");

    private final LabLocationReferenceChecker locationReferenceChecker;

    public LabConfigurationValidator(LabLocationReferenceChecker locationReferenceChecker) {
        this.locationReferenceChecker = locationReferenceChecker;
    }

    public ValidationResult validate(LabConfigEntity configuration,
                                     List<LabConfigObjectEntity> objects,
                                     List<LabConfigLinkEntity> links) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (isBlank(configuration.getMapName())
                || isBlank(configuration.getMapVersion())
                || isBlank(configuration.getMapFileRef())) {
            issues.add(issue("MISSING_MAP", "地图名称、版本和图片地址必须完整", "CONFIG", configuration.getId()));
        }

        Map<Long, LabConfigObjectEntity> objectById = new HashMap<>();
        Map<String, Set<String>> objectCodesByKind = new HashMap<>();
        Set<Long> nodeLocations = new HashSet<>();
        Set<Long> pointLocations = new HashSet<>();
        for (LabConfigObjectEntity object : objects) {
            objectById.put(object.getId(), object);
            validateCommonObject(object, issues);
            if (!isBlank(object.getKind()) && !isBlank(object.getCode())) {
                Set<String> codes = objectCodesByKind.computeIfAbsent(object.getKind(), ignored -> new HashSet<>());
                if (!codes.add(normalizeCode(object.getCode()))) {
                    issues.add(issue("DUPLICATE_OBJECT_CODE", "同类配置对象编码不能重复", "OBJECT", object.getId()));
                }
            }
            if (LabObjectKind.TRAFFIC_NODE.name().equals(object.getKind())) {
                validateMapPose(object, issues);
                validateLocationBinding(object, nodeLocations, issues);
            } else if (LabObjectKind.MACHINE.name().equals(object.getKind())) {
                validateMapPose(object, issues);
            } else if (LabObjectKind.MACHINE_POINT.name().equals(object.getKind())) {
                validatePointPose(object, issues);
                validateLocationBinding(object, pointLocations, issues);
            } else {
                issues.add(issue("INVALID_OBJECT_KIND", "配置对象类别不受支持", "OBJECT", object.getId()));
            }
        }

        for (LabConfigObjectEntity object : objects) {
            if (!LabObjectKind.MACHINE_POINT.name().equals(object.getKind())) {
                continue;
            }
            requireReference(objectById, object.getParentId(), LabObjectKind.MACHINE,
                    "BROKEN_MACHINE_REFERENCE", "点位所属机台不存在或不属于当前配置", object.getId(), issues);
            if (object.getNavObjectId() != null) {
                requireReference(objectById, object.getNavObjectId(), LabObjectKind.TRAFFIC_NODE,
                        "BROKEN_NAV_REFERENCE", "点位关联导航节点不存在或不属于当前配置", object.getId(), issues);
            }
        }

        Set<String> linkCodes = new HashSet<>();
        for (LabConfigLinkEntity link : links) {
            if (isBlank(link.getCode())) {
                issues.add(issue("INCOMPLETE_LINK", "连接编码不能为空", "LINK", link.getId()));
            } else if (!linkCodes.add(normalizeCode(link.getCode()))) {
                issues.add(issue("DUPLICATE_LINK_CODE", "连接编码不能重复", "LINK", link.getId()));
            }
            if (link.getStartObjectId().equals(link.getEndObjectId())) {
                issues.add(issue("SELF_LINK", "连接起点和终点不能相同", "LINK", link.getId()));
            }
            requireReference(objectById, link.getStartObjectId(), LabObjectKind.TRAFFIC_NODE,
                    "BROKEN_LINK_START", "连接起点不存在或不属于当前配置", link.getId(), issues);
            requireReference(objectById, link.getEndObjectId(), LabObjectKind.TRAFFIC_NODE,
                    "BROKEN_LINK_END", "连接终点不存在或不属于当前配置", link.getId(), issues);
            if (link.getSpeedLimit() == null || link.getSpeedLimit().signum() <= 0) {
                issues.add(issue("INVALID_SPEED_LIMIT", "连接限速必须大于0", "LINK", link.getId()));
            }
            if (!isEnumValue(LabLinkDirection.class, link.getDirection())) {
                issues.add(issue("INVALID_DIRECTION", "连接方向不受支持", "LINK", link.getId()));
            }
        }
        return new ValidationResult(issues.isEmpty(), issues);
    }

    private void validateCommonObject(LabConfigObjectEntity object, List<ValidationIssue> issues) {
        if (isBlank(object.getCode()) || isBlank(object.getName()) || isBlank(object.getType())) {
            issues.add(issue("INCOMPLETE_OBJECT", "对象编码、名称和类型必须完整", "OBJECT", object.getId()));
        }
    }

    private void validateMapPose(LabConfigObjectEntity object, List<ValidationIssue> issues) {
        if (!CoordinateFrame.MAP.name().equals(object.getCoordinateFrame())
                || object.getX() == null || object.getY() == null || !validAngle(object.getRz())) {
            issues.add(issue("INVALID_MAP_POSE", "地图坐标必须包含X、Y和-180至180度的朝向角", "OBJECT", object.getId()));
        }
    }

    private void validatePointPose(LabConfigObjectEntity object, List<ValidationIssue> issues) {
        if (!isEnumValue(CoordinateFrame.class, object.getCoordinateFrame())
                || object.getX() == null || object.getY() == null || object.getZ() == null
                || !validAngle(object.getRx()) || !validAngle(object.getRy()) || !validAngle(object.getRz())) {
            issues.add(issue("INVALID_POINT_POSE", "点位坐标系和六维位姿必须完整有效", "OBJECT", object.getId()));
        }
    }

    private void validateLocationBinding(LabConfigObjectEntity object,
                                         Set<Long> boundLocations,
                                         List<ValidationIssue> issues) {
        if (object.getLocationId() == null) {
            return;
        }
        if (!boundLocations.add(object.getLocationId())) {
            issues.add(issue("DUPLICATE_LOCATION_BINDING", "同一配置内库位绑定重复", "OBJECT", object.getId()));
        }
        if (!locationReferenceChecker.exists(object.getLocationId())) {
            issues.add(issue("UNKNOWN_LOCATION", "关联库位不存在", "OBJECT", object.getId()));
        }
    }

    private void requireReference(Map<Long, LabConfigObjectEntity> objects,
                                  Long referenceId,
                                  LabObjectKind expectedKind,
                                  String code,
                                  String message,
                                  Long ownerId,
                                  List<ValidationIssue> issues) {
        LabConfigObjectEntity reference = referenceId == null ? null : objects.get(referenceId);
        if (reference == null || !expectedKind.name().equals(reference.getKind())) {
            issues.add(issue(code, message, "OBJECT", ownerId));
        }
    }

    private boolean validAngle(BigDecimal value) {
        return value != null && value.compareTo(MIN_ANGLE) >= 0 && value.compareTo(MAX_ANGLE) <= 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private <E extends Enum<E>> boolean isEnumValue(Class<E> enumType, String value) {
        if (value == null) {
            return false;
        }
        try {
            Enum.valueOf(enumType, value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private ValidationIssue issue(String code, String message, String type, Long id) {
        return new ValidationIssue(code, message, type, id);
    }
}
