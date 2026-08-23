package com.kunling.scheduling.agvflow.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class LabNodeView {
    private Long id;
    private String code;
    private String name;
    private String type;
    private Long locationId;
    private BigDecimal x;
    private BigDecimal y;
    private BigDecimal yaw;
}
