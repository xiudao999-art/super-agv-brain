package com.kunling.scheduling.workflow.order.client;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class PulledTask {
    private Integer taskSeq;
    private String taskName;
    private String flowNumber;

    @Schema(description = "流程模板ID，对应flow_template表主键", example = "1001")
    private Long flowTemplateId;

    @Schema(description = "当前任务的上游执行参数；原样保存为JSON")
    private List<Map<String, Object>> items = new ArrayList<>();
}
