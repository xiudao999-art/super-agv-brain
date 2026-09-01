package com.kunling.scheduling.app.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LabConfigSummary {
    private Long id;
    private Integer revision;
    private String status;
    private LabMapView map;
    private LabConfigCounts counts;
}
