package com.kunling.scheduling.app.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LabConfigVersionResult {
    private Long configId;
    private Integer revision;
    private String status;
}
