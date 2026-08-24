package com.kunling.scheduling.agvflow.service;

import com.kunling.scheduling.agvflow.domain.dto.LabMapPointView;
import com.kunling.scheduling.agvflow.domain.entity.LabConfigObjectEntity;
import com.kunling.scheduling.agvflow.enums.CoordinateFrame;
import com.kunling.scheduling.agvflow.enums.LabObjectKind;
import com.kunling.scheduling.common.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LabMapPointProjectorTest {

    private final LabMapPointProjector projector = new LabMapPointProjector();

    @Test
    void 机台坐标点位会换算为地图坐标并归一化角度() {
        LabConfigObjectEntity machine = object(1L, LabObjectKind.MACHINE, CoordinateFrame.MAP,
                "10", "20", "90");
        LabConfigObjectEntity localPoint = object(2L, LabObjectKind.MACHINE_POINT, CoordinateFrame.MACHINE,
                "1", "2", "30");
        localPoint.setParentId(machine.getId());
        localPoint.setLocationId(9L);
        LabConfigObjectEntity mapPoint = object(3L, LabObjectKind.MACHINE_POINT, CoordinateFrame.MAP,
                "5.5", "6.5", "-180");
        mapPoint.setParentId(machine.getId());

        List<LabMapPointView> result = projector.project(Arrays.asList(machine, localPoint, mapPoint));

        assertEquals(3, result.size());
        assertDecimal("8", result.get(1).getX());
        assertDecimal("21", result.get(1).getY());
        assertDecimal("120", result.get(1).getYaw());
        assertEquals(Long.valueOf(9L), result.get(1).getLocationId());
        assertDecimal("5.5", result.get(2).getX());
        assertDecimal("6.5", result.get(2).getY());
        assertDecimal("-180", result.get(2).getYaw());
    }

    @Test
    void 机台坐标点位缺少所属机台时拒绝返回错误坐标() {
        LabConfigObjectEntity point = object(2L, LabObjectKind.MACHINE_POINT, CoordinateFrame.MACHINE,
                "1", "2", "30");
        point.setParentId(999L);

        assertThrows(ConflictException.class, () -> projector.project(Collections.singletonList(point)));
    }

    private LabConfigObjectEntity object(Long id,
                                         LabObjectKind kind,
                                         CoordinateFrame frame,
                                         String x,
                                         String y,
                                         String yaw) {
        LabConfigObjectEntity object = new LabConfigObjectEntity();
        object.setId(id);
        object.setCode(kind.name() + "-" + id);
        object.setName(kind.name());
        object.setKind(kind.name());
        object.setType("TEST");
        object.setCoordinateFrame(frame.name());
        object.setX(new BigDecimal(x));
        object.setY(new BigDecimal(y));
        object.setRz(new BigDecimal(yaw));
        return object;
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
