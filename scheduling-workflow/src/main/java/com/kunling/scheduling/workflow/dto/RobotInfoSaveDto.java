package com.kunling.scheduling.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "机器人信息保存参数")
public class RobotInfoSaveDto {

    @Schema(description = "主键ID；为空时新增，非空时修改")
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
}
