package com.kunling.scheduling.agvflow.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ValidationIssue {
    private String code;
    private String message;
    private String entityType;
    private Long entityId;
}
