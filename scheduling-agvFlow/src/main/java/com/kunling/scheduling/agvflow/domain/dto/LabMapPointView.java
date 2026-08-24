package com.kunling.scheduling.agvflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@Schema(description = "地图点位投影视图，坐标统一为地图坐标系")
public class LabMapPointView {

    @Schema(description = "配置对象ID")
    private Long id;

    @Schema(description = "对象类别：TRAFFIC_NODE、MACHINE或MACHINE_POINT")
    private String kind;

    private String code;
    private String name;
    private String type;

    @Schema(description = "可选的关联库位ID")
    private Long locationId;

    @Schema(description = "地图X坐标，单位为米")
    private BigDecimal x;

    @Schema(description = "地图Y坐标，单位为米")
    private BigDecimal y;

    @Schema(description = "地图朝向角，单位为度，范围为-180至180")
    private BigDecimal yaw;
}
