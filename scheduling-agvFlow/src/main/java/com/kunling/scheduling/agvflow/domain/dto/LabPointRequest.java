package com.kunling.scheduling.agvflow.domain.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

@Data
public class LabPointRequest {

    @NotNull(message = "所属机台不能为空")
    private Long machineId;

    private Long locationId;

    private Long navNodeId;

    @NotBlank(message = "点位编码不能为空")
    @Size(max = 64, message = "点位编码不能超过64个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "点位编码格式不正确")
    private String code;

    @NotBlank(message = "点位名称不能为空")
    @Size(max = 128, message = "点位名称不能超过128个字符")
    private String name;

    @NotBlank(message = "点位类型不能为空")
    @Size(max = 64, message = "点位类型不能超过64个字符")
    private String type;

    @NotBlank(message = "坐标系不能为空")
    @Pattern(regexp = "MAP|MACHINE", message = "坐标系只能是MAP或MACHINE")
    private String frame;

    @NotNull(message = "X坐标不能为空")
    @Digits(integer = 8, fraction = 4, message = "X坐标最多保留4位小数")
    private BigDecimal x;

    @NotNull(message = "Y坐标不能为空")
    @Digits(integer = 8, fraction = 4, message = "Y坐标最多保留4位小数")
    private BigDecimal y;

    @NotNull(message = "Z坐标不能为空")
    @Digits(integer = 8, fraction = 4, message = "Z坐标最多保留4位小数")
    private BigDecimal z;

    @NotNull(message = "RX不能为空")
    @DecimalMin(value = "-180", message = "RX不能小于-180度")
    @DecimalMax(value = "180", message = "RX不能大于180度")
    private BigDecimal rx;

    @NotNull(message = "RY不能为空")
    @DecimalMin(value = "-180", message = "RY不能小于-180度")
    @DecimalMax(value = "180", message = "RY不能大于180度")
    private BigDecimal ry;

    @NotNull(message = "RZ不能为空")
    @DecimalMin(value = "-180", message = "RZ不能小于-180度")
    @DecimalMax(value = "180", message = "RZ不能大于180度")
    private BigDecimal rz;
}
