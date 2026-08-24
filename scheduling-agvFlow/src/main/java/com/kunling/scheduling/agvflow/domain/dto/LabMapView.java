package com.kunling.scheduling.agvflow.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LabMapView {
    private String name;
    private String version;
    private String imageUrl;
}
