package com.kunling.scheduling.workflow.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kunling.scheduling.workflow.entity.HardwareInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Schema(description = "Robot information and module details")
public class RobotInfoResp {

    @Schema(description = "Primary key")
    private Long id;

    private String robotCode;
    private String robotName;
    private Long mapId;
    private String mapName;
    private String mapVersion;
    private String currentLocationCode;
    private Integer connectionStatus;
    private Integer runningStatus;

    @Schema(description = "Module status summary: normal count/abnormal count")
    private String moduleStatus;

    private BigDecimal batteryLevel;
    private Integer enabled;
    private String remark;
    private Long createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private Long updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    @Schema(description = "Modules assigned to this robot")
    private List<HardwareInfo> modules;
}
