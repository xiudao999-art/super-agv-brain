package com.kunling.scheduling.workflow.order.client;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class PulledTask {
    private Integer taskSeq;
    private String taskName;
    private String flowNumber;

    @Schema(description = "流程模板ID，对应flow_template表主键", example = "1001")
    private Long flowTemplateId;
}
