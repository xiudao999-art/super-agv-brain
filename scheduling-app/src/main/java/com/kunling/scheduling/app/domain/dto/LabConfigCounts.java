package com.kunling.scheduling.app.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LabConfigCounts {
    private int nodeCount;
    private int machineCount;
    private int pointCount;
    private int linkCount;

    public static LabConfigCounts empty() {
        return new LabConfigCounts(0, 0, 0, 0);
    }
}
