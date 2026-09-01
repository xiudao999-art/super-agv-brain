package com.kunling.scheduling.app.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kunling.scheduling.app.enums.FailureStrategyEnums;
import com.kunling.scheduling.app.enums.NodeCategoryEnums;
import com.kunling.scheduling.app.enums.NodeState;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class FlowTemplateDetail {
    private final Long id;
    private final String templateNumber;
    private final String templateName;
    private final Integer status;
    private final Integer version;
    private final String applicableScope;
    private final List<NodeDetail> nodes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final Date updateTime;

    public FlowTemplateDetail(Long id, String templateNumber, String templateName,
                               Integer status, Integer version, String applicableScope,
                               List<NodeDetail> nodes, Date createTime, Date updateTime) {
        this.id = id;
        this.templateNumber = templateNumber;
        this.templateName = templateName;
        this.status = status;
        this.version = version;
        this.applicableScope = applicableScope;
        this.nodes = nodes;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    @Data
    public static class NodeDetail {
        private final Long id;
        private final String nodeName;
        private final String nodeCode;
        private final Integer sort;
        private final NodeState status;
        private final NodeCategoryEnums nodeCategory;
        private final FailureStrategyEnums failureStrategy;
        private final Long parentNodeId;
        private final String completionCriteria;
        private final Long leftNodeId;
        private final Long rightNodeId;
        private final List<ActionDetail> actions;

        public NodeDetail(Long id, String nodeName, String nodeCode, Integer sort, NodeState status,
                          NodeCategoryEnums nodeCategory, FailureStrategyEnums failureStrategy,
                          Long parentNodeId,
                          String completionCriteria, Long leftNodeId, Long rightNodeId,
                          List<ActionDetail> actions) {
            this.id = id;
            this.nodeName = nodeName;
            this.nodeCode = nodeCode;
            this.sort = sort;
            this.status = status;
            this.nodeCategory = nodeCategory;
            this.failureStrategy = failureStrategy;
            this.parentNodeId = parentNodeId;
            this.completionCriteria = completionCriteria;
            this.leftNodeId = leftNodeId;
            this.rightNodeId = rightNodeId;
            this.actions = actions;
        }
    }

    @Data
    public static class ActionDetail {
        private final Long id;
        private final Long machineId;
        private final String actionName;
        private final String actionCode;
        private final String nodeCategory;
        private final String applicableScope;
        private final FailureStrategyEnums failureStrategy;
        private final String completionCriteria;
        private final Long nextActionId;

        public ActionDetail(Long id, Long machineId, String actionName, String actionCode,
                             String nodeCategory, String applicableScope,
                             FailureStrategyEnums failureStrategy,
                             String completionCriteria, Long nextActionId) {
            this.id = id;
            this.machineId = machineId;
            this.actionName = actionName;
            this.actionCode = actionCode;
            this.nodeCategory = nodeCategory;
            this.applicableScope = applicableScope;
            this.failureStrategy = failureStrategy;
            this.completionCriteria = completionCriteria;
            this.nextActionId = nextActionId;
        }

        public Long getId() { return id; }
        public Long getMachineId() { return machineId; }
        public String getActionName() { return actionName; }
        public String getActionCode() { return actionCode; }
        public String getNodeCategory() { return nodeCategory; }
        public String getApplicableScope() { return applicableScope; }
    }

    public Long getId() { return id; }
    public String getTemplateNumber() { return templateNumber; }
    public String getTemplateName() { return templateName; }
    public Integer getStatus() { return status; }
    public Integer getVersion() { return version; }
    public String getApplicableScope() { return applicableScope; }
    public List<NodeDetail> getNodes() { return nodes; }
    public Date getCreateTime() { return createTime; }
    public Date getUpdateTime() { return updateTime; }
}
