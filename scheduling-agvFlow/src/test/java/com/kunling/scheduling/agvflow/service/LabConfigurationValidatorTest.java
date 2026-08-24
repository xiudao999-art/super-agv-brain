package com.kunling.scheduling.agvflow.service;

import com.kunling.scheduling.agvflow.service.LabConfigurationValidator;
import com.kunling.scheduling.agvflow.service.LabLocationReferenceChecker;
import com.kunling.scheduling.agvflow.enums.CoordinateFrame;
import com.kunling.scheduling.agvflow.enums.LabConfigStatus;
import com.kunling.scheduling.agvflow.enums.LabLinkDirection;
import com.kunling.scheduling.agvflow.enums.LabObjectKind;
import com.kunling.scheduling.agvflow.domain.dto.ValidationResult;
import com.kunling.scheduling.agvflow.domain.entity.LabConfigEntity;
import com.kunling.scheduling.agvflow.domain.entity.LabConfigLinkEntity;
import com.kunling.scheduling.agvflow.domain.entity.LabConfigObjectEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LabConfigurationValidatorTest {

    private final LabLocationReferenceChecker locationChecker = mock(LabLocationReferenceChecker.class);
    private final LabConfigurationValidator validator = new LabConfigurationValidator(locationChecker);

    @Test
    void 完整对象图可以通过发布校验() {
        LabConfigEntity config = validConfig();
        LabConfigObjectEntity node = mapObject(1L, LabObjectKind.TRAFFIC_NODE, "N01");
        LabConfigObjectEntity machine = mapObject(2L, LabObjectKind.MACHINE, "M01");
        LabConfigObjectEntity point = point(3L, machine.getId(), node.getId(), 10L);
        LabConfigLinkEntity link = link(1L, node.getId(), 4L, new BigDecimal("0.5"));
        LabConfigObjectEntity secondNode = mapObject(4L, LabObjectKind.TRAFFIC_NODE, "N02");
        when(locationChecker.exists(10L)).thenReturn(true);

        ValidationResult result = validator.validate(
                config, Arrays.asList(node, machine, point, secondNode), Collections.singletonList(link));

        assertTrue(result.isValid());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void 非法限速和重复库位绑定会返回结构化问题() {
        LabConfigEntity config = validConfig();
        LabConfigObjectEntity firstNode = mapObject(1L, LabObjectKind.TRAFFIC_NODE, "N01");
        firstNode.setLocationId(10L);
        LabConfigObjectEntity secondNode = mapObject(2L, LabObjectKind.TRAFFIC_NODE, "N02");
        secondNode.setLocationId(10L);
        LabConfigLinkEntity link = link(1L, firstNode.getId(), secondNode.getId(), BigDecimal.ZERO);
        when(locationChecker.exists(10L)).thenReturn(true);

        ValidationResult result = validator.validate(
                config, Arrays.asList(firstNode, secondNode), Collections.singletonList(link));

        assertFalse(result.isValid());
        assertEquals(2, result.getIssues().size());
        assertEquals("DUPLICATE_LOCATION_BINDING", result.getIssues().get(0).getCode());
        assertEquals("INVALID_SPEED_LIMIT", result.getIssues().get(1).getCode());
    }

    private LabConfigEntity validConfig() {
        LabConfigEntity config = new LabConfigEntity();
        config.setId(1L);
        config.setMapName("实验室地图");
        config.setMapVersion("V1");
        config.setMapFileRef("map://lab/v1");
        config.setStatus(LabConfigStatus.DRAFT.name());
        return config;
    }

    private LabConfigObjectEntity mapObject(Long id, LabObjectKind kind, String code) {
        LabConfigObjectEntity object = new LabConfigObjectEntity();
        object.setId(id);
        object.setConfigId(1L);
        object.setCode(code);
        object.setName(code);
        object.setKind(kind.name());
        object.setType("TEST");
        object.setCoordinateFrame(CoordinateFrame.MAP.name());
        object.setX(BigDecimal.ZERO);
        object.setY(BigDecimal.ZERO);
        object.setRz(BigDecimal.ZERO);
        return object;
    }

    private LabConfigObjectEntity point(Long id, Long machineId, Long navNodeId, Long locationId) {
        LabConfigObjectEntity point = mapObject(id, LabObjectKind.MACHINE_POINT, "P01");
        point.setParentId(machineId);
        point.setNavObjectId(navNodeId);
        point.setLocationId(locationId);
        point.setCoordinateFrame(CoordinateFrame.MACHINE.name());
        point.setZ(BigDecimal.ZERO);
        point.setRx(BigDecimal.ZERO);
        point.setRy(BigDecimal.ZERO);
        return point;
    }

    private LabConfigLinkEntity link(Long id, Long startId, Long endId, BigDecimal speedLimit) {
        LabConfigLinkEntity link = new LabConfigLinkEntity();
        link.setId(id);
        link.setConfigId(1L);
        link.setCode("L01");
        link.setStartObjectId(startId);
        link.setEndObjectId(endId);
        link.setDirection(LabLinkDirection.ONE_WAY.name());
        link.setSpeedLimit(speedLimit);
        return link;
    }
}
