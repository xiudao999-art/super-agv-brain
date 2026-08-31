package com.kunling.scheduling.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.kunling.scheduling.workflow.enums.FailureStrategyEnums;
import com.kunling.scheduling.workflow.enums.NodeCategoryEnums;
import com.kunling.scheduling.workflow.enums.NodeState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@TableName(value = "flow_node", autoResultMap = true)
public class FlowNode extends BaseEntity {
    @Schema(description = "订单任务ID")
    private Long taskId;

    @Schema(description = "processId")
    private String processInstanceId;

    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "节点编码")
    private String nodeCode;

    @Schema(description = "Action 定义ID")
    private String actionDefinitionId;

    @Schema(description = "本次 Action 执行实例ID")
    private String actionInstanceId;


    @Schema(description = "机器人")
    private String robotId;

    @Schema(description = "节点顺序")
    private Integer sort;

    @Schema(description = "节点动作ID列表")
    private NodeState status;

    @Schema(description = "节点动作")
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
