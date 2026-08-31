package com.kunling.scheduling.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hardware_info")
@Schema(description = "硬件信息")
public class HardwareInfo extends BaseEntity {

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "设备类型：AGV/PERIPHERAL")
    private String deviceType;

    @Schema(description = "所属AGV ID")
    private Long parentId;

    @Schema(description = "硬件厂商")
    private String manufacturer;

    @Schema(description = "硬件型号")
    private String hardwareModel;

    @Schema(description = "通信协议")
    private String communicationProtocol;

    @Schema(description = "IP地址")
    private String ipAddress;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "状态：0-离线，1-在线，2-故障")
    private Integer status;

    @Schema(description = "备注")
    private String remark;
}
