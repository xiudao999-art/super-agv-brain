package com.kunling.scheduling.agvflow.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class LabPointView {
    private Long id;
    private Long machineId;
    private Long locationId;
    private Long navNodeId;
    private String code;
    private String name;
    private String type;
    private String frame;
    private BigDecimal x;
    private BigDecimal y;
    private BigDecimal z;
    private BigDecimal rx;
    private BigDecimal ry;
    private BigDecimal rz;
}
