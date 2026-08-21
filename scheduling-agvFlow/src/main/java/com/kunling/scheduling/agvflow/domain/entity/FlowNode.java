package com.kunling.scheduling.agvflow.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.kunling.scheduling.agvflow.enums.FailureStrategyEnums;
import com.kunling.scheduling.agvflow.enums.NodeCategoryEnums;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@TableName(value = "flow_node", autoResultMap = true)
public class FlowNode extends BaseEntity {
    @Schema(description = "模版id")
    private Long templateId;

    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "节点编码")
    private String nodeCode;

    @Schema(description = "节点顺序")
    private Integer sort;

    @Schema(description = "节点动作ID列表")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> actions;

    @Schema(description = "节点分类: GENERAL/MAIN/OTHER")
    private NodeCategoryEnums nodeCategory;

    @Schema(description = "失败策略")
    private FailureStrategyEnums failureStrategy;

    @Schema(description = "完成依据")
    private String completionCriteria;

    @Schema(description = "左侧连接节点ID")
    private Long leftNodeId;

    @Schema(description = "右侧连接节点ID")
    private Long rightNodeId;

    @Schema(description = "父节点ID(子流程用)")
    private Long parentNodeId;
}
