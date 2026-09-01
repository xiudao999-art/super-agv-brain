package com.kunling.scheduling.app.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LabConfigDetail {
    private Long id;
    private String labName;
    private Integer revision;
    private String status;
    private LabMapView map;
    private List<LabNodeView> nodes;
    private List<LabMachineView> machines;
    private List<LabPointView> points;
    private List<LabLinkView> links;
}
