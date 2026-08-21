package com.kunling.scheduling.agvflow.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("carrier")
@Schema(description = "载具")
public class Carrier extends BaseEntity {

    @Schema(description = "载具唯一编码")
    private String carrierCode;
    @Schema(description = "载具条码")
    private String barcode;
    @Schema(description = "载具类型ID")
    private Long carrierTypeId;
    @Schema(description = "当前所在库位ID")
    private Long currentLocationId;
    @Schema(description = "载具状态", example = "IDLE")
    private String carrierStatus;
    @Schema(description = "关联业务订单编码")
    private String relatedOrderCode;
    @Schema(description = "最后扫描时间")
    private LocalDateTime lastScanTime;
    @Schema(description = "启用状态：0-停用，1-启用", example = "1")
    private Integer enabled;
    @Schema(description = "备注")
    private String remark;

}
