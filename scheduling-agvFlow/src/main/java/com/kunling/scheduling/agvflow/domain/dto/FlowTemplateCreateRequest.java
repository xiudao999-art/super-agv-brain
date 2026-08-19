package com.kunling.scheduling.agvflow.domain.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class FlowTemplateCreateRequest {
    @NotBlank
    private String templateNumber;
    @NotBlank
    private String templateName;
    @NotBlank
    private String version;
    @NotNull
    private Integer status;
    @Valid
    @NotEmpty
    private List<NodeRequest> nodes;


    @Data
    public static class NodeRequest {
        @NotBlank
        private String nodeName;
        @NotBlank
        private String nodeCode;
        @NotNull
        private Integer sort;
        @Valid
        @NotEmpty
        private List<ActionRequest> actions;


    }

    @Data
    public static class ActionRequest {
        @NotNull
        private Integer id;
        private Integer machineId;
        @NotBlank
        private String actionName;
        @NotBlank
        private String actionCode;


    }
}
