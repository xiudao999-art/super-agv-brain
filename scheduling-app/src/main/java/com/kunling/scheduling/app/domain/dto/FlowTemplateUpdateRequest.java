package com.kunling.scheduling.app.domain.dto;

import com.kunling.scheduling.app.enums.FailureStrategyEnums;
import com.kunling.scheduling.app.enums.NodeCategoryEnums;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Schema(description = "流程模板更新请求")
public class FlowTemplateUpdateRequest {

    @Schema(description = "模板名称", required = true, example = "入库流程模板")
    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    @Schema(description = "模板状态 0:未启用 1:启用", required = true, example = "1")
    @NotNull(message = "模板状态不能为空")
    private Integer status;

    @NotNull(message = "版本号不能为空")
    private Integer version;

    @Schema(description = "适用对象", example = "AGV-A")
    private String applicableScope;

    @Schema(description = "节点列表（增量增删改：id存在则更新，不存在则新增，未提交则删除）")
    @Valid
    private List<NodeRequest> nodes;

    @Data
    @Schema(description = "节点更新请求")
    public static class NodeRequest {
        @Schema(description = "节点ID（存在则更新，不传则新增）")
        private Long id;

        @Schema(description = "节点名称", required = true, example = "起始节点")
        @NotBlank(message = "节点名称不能为空")
        private String nodeName;

        @Schema(description = "节点编码", required = true, example = "NODE_START")
        @NotBlank(message = "节点编码不能为空")
        private String nodeCode;

        @Schema(description = "节点顺序（升序排列）", required = true, example = "1")
        @NotNull(message = "节点顺序不能为空")
        private Integer sort;

        @Schema(description = "节点分类: GENERAL/MAIN/OTHER", example = "MAIN")
        private NodeCategoryEnums nodeCategory;

        @Schema(description = "失败策略", example = "RETRY")
        private FailureStrategyEnums failureStrategy;

        @Schema(description = "完成依据")
        private String completionCriteria;

        @Schema(description = "左侧连接节点ID")
        private Long leftNodeId;

        @Schema(description = "右侧连接节点ID")
        private Long rightNodeId;

        @Schema(description = "父节点ID（子流程用）")
        private Long parentNodeId;

        @Schema(description = "节点动作列表")
        @Valid
        private List<ActionRequest> actions;
    }

    @Data
    @Schema(description = "动作更新请求")
    public static class ActionRequest {
        @Schema(description = "动作ID（存在则直接引用，不传则按编码查找或新建）")
        private Long id;

        @Schema(description = "所属节点ID")
        private Long machineId;

        @Schema(description = "动作名称", required = true, example = "取货")
        @NotBlank(message = "动作名称不能为空")
        private String actionName;

        @Schema(description = "动作编码", required = true, example = "ACTION_PICK")
        @NotBlank(message = "动作编码不能为空")
        private String actionCode;

        @Schema(description = "节点分类: GENERAL/MAIN/OTHER", example = "MAIN")
        private String nodeCategory;

        @Schema(description = "适用对象", example = "AGV-A")
        private String applicableScope;

        @Schema(description = "失败策略")
        private FailureStrategyEnums failureStrategy;

        @Schema(description = "完成依据")
        private String completionCriteria;

        @Schema(description = "后继子节点ID")
        private Long nextActionId;
    }
}
