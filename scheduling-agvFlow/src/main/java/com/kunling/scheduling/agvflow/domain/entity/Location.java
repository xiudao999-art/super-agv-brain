package com.kunling.scheduling.agvflow.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("location")
@Schema(description = "库位")
public class Location extends BaseEntity{
    @Schema(description = "库位编码")
    private String locationCode;
    @Schema(description = "库位名称")
    private String locationName;
    @Schema(description = "库位类型")
    private String locationType;
    @Schema(description = "兼容载具类型，多个类型以逗号分隔")
    private String compatibleCarrierType;
    @Schema(description = "状态来源")
    private String statusSource;
    @Schema(description = "占用状态：0-空闲，1-占用", example = "0")
    private Integer occupancyStatus;
    @Schema(description = "当前占用载具编码")
    private String currentCarrierCode;
    @Schema(description = "最后核对时间")
    private LocalDateTime lastCheckTime;
    @Schema(description = "启用状态：0-停用，1-启用", example = "1")
    private Integer enabled;
    @Schema(description = "备注")
    private String remark;
}
