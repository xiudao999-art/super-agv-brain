package com.kunling.scheduling.agvflow.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("location_type")
@Schema(description = "库位类型")
public class LocationType extends BaseEntity {
    @Schema(description = "库位类型编码")
    private String typeCode;
    @Schema(description = "库位类型名称")
    private String typeName;
    @Schema(description = "最大库位容量", example = "1")
    private Integer capacity;
    @Schema(description = "兼容载具类型，多个类型以逗号分隔")
    private String compatibleCarrierTypes;
    @Schema(description = "状态判断来源")
    private String statusSource;
    @Schema(description = "互斥规则")
    private String mutexRule;
    @Schema(description = "状态：0-停用，1-启用", example = "1")
    private Integer status;
    @Schema(description = "备注")
    private String remark;
}
