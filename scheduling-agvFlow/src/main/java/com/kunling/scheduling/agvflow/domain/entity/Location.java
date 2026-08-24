package com.kunling.scheduling.agvflow.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("location")
@Schema(description = "库位")
public class Location implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "库位编码")
    private String locationCode;
    @Schema(description = "库位名称")
    private String locationName;
    @Schema(description = "库位类型")
    private String locationType;
    @Schema(description = "所属空间")
    private String spaceName;
    @Schema(description = "所属地图")
    private String mapName;
    @Schema(description = "所属设备或区域")
    private String ownerName;
    @Schema(description = "坐标类型")
    private String coordinateType;
    @Schema(description = "地图X坐标，单位：米")
    private BigDecimal mapX;
    @Schema(description = "地图Y坐标，单位：米")
    private BigDecimal mapY;
    @Schema(description = "地图偏航角，单位：度")
    private BigDecimal mapYaw;
    @Schema(description = "AGV到达导航点编码")
    private String navPointCode;
    @Schema(description = "取放操作点位地址")
    private String operationPoint;
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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
