package com.kunling.scheduling.agvflow.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LabSpaceSummary {
    private String id;
    private String code;
    private String name;
    private LabConfigSummary published;
    private LabConfigSummary draft;
}
