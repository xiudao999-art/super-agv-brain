package com.kunling.scheduling.app.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class LabLinkView {
    private Long id;
    private String code;
    private Long startNodeId;
    private Long endNodeId;
    private String direction;
    private BigDecimal speedLimit;
}
