package com.kunling.scheduling.agvflow.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("flow_action")
public class FlowAction extends BaseEntity {


    @Schema(description = "所属节点")
    private Integer machineId;

    @Schema(description = "动作名称")
    private String actionName;

    @Schema(description = "动作编码")
    private String actionCode;

}
