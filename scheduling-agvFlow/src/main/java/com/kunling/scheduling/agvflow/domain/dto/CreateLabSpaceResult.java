package com.kunling.scheduling.agvflow.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateLabSpaceResult {
    private String spaceId;
    private Long configId;
    private Integer revision;
    private String status;
}
