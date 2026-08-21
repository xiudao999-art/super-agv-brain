package com.kunling.scheduling.agvflow.domain.dto;

import com.kunling.scheduling.agvflow.enums.FailureStrategyEnums;
import com.kunling.scheduling.agvflow.enums.NodeCategoryEnums;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class FlowTemplateCreateRequest {
    @Schema(description = "模版编号")
    private String templateNumber;

    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    @NotNull(message = "模板状态不能为空")
    private Integer status;

    @NotNull(message = "版本号")
    private Integer version;

    private String applicableScope;

    @Valid
    private List<NodeRequest> nodes;

    @Data
    public static class NodeRequest {
        @NotBlank(message = "节点编码不能为空")
        private String nodeCode;

        @NotBlank(message = "节点名称不能为空")
        private String nodeName;

        @NotNull(message = "节点顺序不能为空")
        private Integer sort;

        @Schema(description = "节点类型")
        private NodeCategoryEnums nodeCategory;

        @Schema(description = "失败策略")
        private FailureStrategyEnums failureStrategy;

        private String completionCriteria;

        private Long leftNodeId;

        private Long rightNodeId;

        @Valid
        private List<ChildNode> childNode;
    }

    @Data
    public static class ChildNode {
        private Long id;

        @NotBlank(message = "动作名称不能为空")
        private String actionName;

        private String actionCode;

        private Long parentNodeId;

        @NotNull(message = "节点顺序不能为空")
        private Integer sort;

        private FailureStrategyEnums failureStrategy;

        private String completionCriteria;

        private Long nextActionId;

    }
}
