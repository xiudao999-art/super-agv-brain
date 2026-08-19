package com.kunling.scheduling.agvflow.domain.dto;

import lombok.Data;

import java.util.List;
@Data
public class FlowTemplateDetail {
    private final Integer id;
    private final String templateNumber;
    private final String templateName;
    private final String version;
    private final Integer status;
    private final List<NodeDetail> nodes;


    @Data
    public static class NodeDetail {
        private final Integer id;
        private final String nodeName;
        private final String nodeCode;
        private final Integer sort;
        private final List<ActionDetail> actions;




    }

    public static class ActionDetail {
        private final Integer id;
        private final Integer machineId;
        private final String actionName;
        private final String actionCode;

        public ActionDetail(Integer id, Integer machineId, String actionName, String actionCode) {
            this.id = id;
            this.machineId = machineId;
            this.actionName = actionName;
            this.actionCode = actionCode;
        }

        public Integer getId() { return id; }
        public Integer getMachineId() { return machineId; }
        public String getActionName() { return actionName; }
        public String getActionCode() { return actionCode; }
    }
}
