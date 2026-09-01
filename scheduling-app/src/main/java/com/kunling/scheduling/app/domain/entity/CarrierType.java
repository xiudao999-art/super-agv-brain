package com.kunling.scheduling.app.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("carrier_type")
@Schema(description = "载具类型")
public class CarrierType extends BaseEntity {
    @Schema(description = "载具类型编码")
    private String typeCode;
    @Schema(description = "载具类型名称")
    private String typeName;
    @Schema(description = "外形尺寸")
    private String dimension;
    @Schema(description = "最大载重，单位：kg")
    private BigDecimal maxWeight;
    @Schema(description = "载具条码规则")
    private String barcodeRule;
    @Schema(description = "状态：DRAFT-草稿，PUBLISHED-已发布，DISABLED-停用", example = "DRAFT")
    private String status;
    @Schema(description = "备注")
    private String remark;
}
