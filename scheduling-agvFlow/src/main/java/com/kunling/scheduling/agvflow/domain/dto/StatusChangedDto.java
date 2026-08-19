package com.kunling.scheduling.agvflow.domain.dto;

import lombok.Data;

@Data
public class StatusChangedDto {
    private String nodeState;

    private String eventCode;
}
