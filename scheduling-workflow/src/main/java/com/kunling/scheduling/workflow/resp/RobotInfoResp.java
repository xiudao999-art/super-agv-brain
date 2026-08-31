package com.kunling.scheduling.workflow.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kunling.scheduling.workflow.entity.HardwareInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Schema(description = "机器人及其模组信息")
public class RobotInfoResp {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "机器人编码")
    private String robotCode;

    @Schema(description = "机器人名称")
    private String robotName;

    @Schema(description = "所属地图ID")
    private Long mapId;

    @Schema(description = "所属地图名称")
    private String mapName;

    @Schema(description = "地图版本")
    private String mapVersion;

    @Schema(description = "当前位置编码")
    private String currentLocationCode;

    @Schema(description = "连接状态：0-离线，1-在线")
    private Integer connectionStatus;

    @Schema(description = "运行状态：0-空闲，1-运行中，2-暂停，3-充电中，4-故障，5-急停")
    private Integer runningStatus;

    @Schema(description = "剩余电量百分比")
    private BigDecimal batteryLevel;

    @Schema(description = "是否启用：0-停用，1-启用")
    private Integer enabled;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建人ID")
    private Long createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新人ID")
    private Long updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private Date updateTime;

    @Schema(description = "机器人下属模组信息")
    private List<HardwareInfo> modules;
}
