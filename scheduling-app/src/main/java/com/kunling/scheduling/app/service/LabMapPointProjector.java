package com.kunling.scheduling.app.service;

import com.kunling.scheduling.app.domain.dto.LabMapPointView;
import com.kunling.scheduling.app.domain.entity.LabConfigObjectEntity;
import com.kunling.scheduling.app.enums.CoordinateFrame;
import com.kunling.scheduling.app.enums.LabObjectKind;
import com.kunling.scheduling.common.exception.ConflictException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将配置对象统一投影到二维地图坐标系，供地图页面直接绘制。
 */
@Component
public class LabMapPointProjector {

    private static final BigDecimal FULL_ANGLE = new BigDecimal("360");
    private static final BigDecimal HALF_ANGLE = new BigDecimal("180");
    private static final int COORDINATE_SCALE = 4;

    public List<LabMapPointView> project(List<LabConfigObjectEntity> objects) {
        Map<Long, LabConfigObjectEntity> machines = new HashMap<>();
        for (LabConfigObjectEntity object : objects) {
            if (LabObjectKind.MACHINE.name().equals(object.getKind())) {
                machines.put(object.getId(), object);
            }
        }

        List<LabMapPointView> points = new ArrayList<>();
        for (LabConfigObjectEntity object : objects) {
            points.add(projectObject(object, machines));
        }
        return points;
    }

    private LabMapPointView projectObject(LabConfigObjectEntity object,
                                           Map<Long, LabConfigObjectEntity> machines) {
        if (LabObjectKind.TRAFFIC_NODE.name().equals(object.getKind())
                || LabObjectKind.MACHINE.name().equals(object.getKind())) {
            requirePose(object);
            if (!CoordinateFrame.MAP.name().equals(object.getCoordinateFrame())) {
                throw invalidConfiguration("节点或机台坐标系不支持地图投影", object.getId());
            }
            return view(object, object.getX(), object.getY(), normalizeAngle(object.getRz()));
        }
        if (LabObjectKind.MACHINE_POINT.name().equals(object.getKind())) {
            return projectMachinePoint(object, machines);
        }
        throw invalidConfiguration("配置对象类别不支持地图投影", object.getId());
    }

    private LabMapPointView projectMachinePoint(LabConfigObjectEntity point,
                                                 Map<Long, LabConfigObjectEntity> machines) {
        requirePose(point);
        if (CoordinateFrame.MAP.name().equals(point.getCoordinateFrame())) {
            return view(point, point.getX(), point.getY(), normalizeAngle(point.getRz()));
        }
        if (!CoordinateFrame.MACHINE.name().equals(point.getCoordinateFrame())) {
            throw invalidConfiguration("机台点位坐标系不支持地图投影", point.getId());
        }

        LabConfigObjectEntity machine = machines.get(point.getParentId());
        if (machine == null) {
            throw invalidConfiguration("机台点位缺少有效的所属机台", point.getId());
        }
        requirePose(machine);

        // 机台局部坐标先绕机台锚点旋转，再平移到地图坐标。
        double radians = Math.toRadians(machine.getRz().doubleValue());
        double localX = point.getX().doubleValue();
        double localY = point.getY().doubleValue();
        BigDecimal mapX = coordinate(machine.getX().doubleValue()
                + localX * Math.cos(radians) - localY * Math.sin(radians));
        BigDecimal mapY = coordinate(machine.getY().doubleValue()
                + localX * Math.sin(radians) + localY * Math.cos(radians));
        BigDecimal mapYaw = normalizeAngle(machine.getRz().add(point.getRz()));
        return view(point, mapX, mapY, mapYaw);
    }

    private void requirePose(LabConfigObjectEntity object) {
        if (object.getX() == null || object.getY() == null || object.getRz() == null) {
            throw invalidConfiguration("配置对象缺少地图投影所需的X、Y或朝向角", object.getId());
        }
    }

    private LabMapPointView view(LabConfigObjectEntity object,
                                 BigDecimal x,
                                 BigDecimal y,
                                 BigDecimal yaw) {
        return new LabMapPointView(
                object.getId(), object.getKind(), object.getCode(), object.getName(), object.getType(),
                object.getLocationId(), x, y, yaw);
    }

    private BigDecimal coordinate(double value) {
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
        return rounded.signum() == 0 ? BigDecimal.ZERO : rounded.stripTrailingZeros();
    }

    private BigDecimal normalizeAngle(BigDecimal value) {
        BigDecimal normalized = value.remainder(FULL_ANGLE);
        if (normalized.compareTo(HALF_ANGLE) > 0) {
            normalized = normalized.subtract(FULL_ANGLE);
        } else if (normalized.compareTo(HALF_ANGLE.negate()) < 0) {
            normalized = normalized.add(FULL_ANGLE);
        }
        BigDecimal rounded = normalized.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
        return rounded.signum() == 0 ? BigDecimal.ZERO : rounded.stripTrailingZeros();
    }

    private ConflictException invalidConfiguration(String message, Long objectId) {
        return new ConflictException(message + ": " + objectId);
    }
}
