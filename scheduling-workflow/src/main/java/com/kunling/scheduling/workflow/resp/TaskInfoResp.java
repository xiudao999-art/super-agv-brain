package com.kunling.scheduling.workflow.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class TaskInfoResp {
    private String upstreamOrderNo;

    private String systemOrderNo;

    private String flowName;

    private String flowTemplateName;

    private String path;

    private String pointPath;

    private String exceptionStrategy;

    private List<TaskAction> taskActionList;

    @Data
    public static class TaskAction {

        private Integer sort;

        private String actionName;

        private String resource;

        private String status;

        private String completeProve;

    }
}
