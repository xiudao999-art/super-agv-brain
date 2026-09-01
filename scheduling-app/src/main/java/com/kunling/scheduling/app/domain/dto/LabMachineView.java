package com.kunling.scheduling.app.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class LabMachineView {
    private Long id;
    private String code;
    private String name;
    private String type;
    private BigDecimal anchorX;
    private BigDecimal anchorY;
    private BigDecimal anchorYaw;
}
