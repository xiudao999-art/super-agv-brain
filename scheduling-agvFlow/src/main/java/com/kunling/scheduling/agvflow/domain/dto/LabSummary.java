package com.kunling.scheduling.agvflow.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LabSummary {
    private String name;
    private LabConfigSummary published;
    private LabConfigSummary draft;
}
