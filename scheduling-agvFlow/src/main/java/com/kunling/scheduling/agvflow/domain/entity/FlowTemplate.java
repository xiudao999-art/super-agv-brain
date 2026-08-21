package com.kunling.scheduling.agvflow.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("flow_template")
public class FlowTemplate extends BaseEntity {
    @Schema(description = "模版编号")
    private String templateNumber;

    @Schema(description = "模版名称")
    private String templateName;

    @Schema(description = "模版状态 0:未启用 1:启用")
    private Integer status;

    @Schema(description = "模板版本号")
    private Integer version;

    @Schema(description = "适用对象")
    private String applicableScope;
}
