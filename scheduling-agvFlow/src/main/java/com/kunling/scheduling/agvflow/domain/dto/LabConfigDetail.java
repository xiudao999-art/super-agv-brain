package com.kunling.scheduling.agvflow.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LabConfigDetail {
    private Long id;
    private String spaceId;
    private String spaceCode;
    private String spaceName;
    private Integer revision;
    private String status;
    private LabMapView map;
    private List<LabNodeView> nodes;
    private List<LabMachineView> machines;
    private List<LabPointView> points;
    private List<LabLinkView> links;
}
