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
public class LabMachineRequest {

    @NotBlank(message = "机台编码不能为空")
    @Size(max = 64, message = "机台编码不能超过64个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "机台编码格式不正确")
    private String code;

    @NotBlank(message = "机台名称不能为空")
    @Size(max = 128, message = "机台名称不能超过128个字符")
    private String name;

    @NotBlank(message = "机台类型不能为空")
    @Size(max = 64, message = "机台类型不能超过64个字符")
    private String type;

    @NotNull(message = "机台锚点X坐标不能为空")
    @Digits(integer = 8, fraction = 4, message = "机台锚点X坐标最多保留4位小数")
    private BigDecimal anchorX;

    @NotNull(message = "机台锚点Y坐标不能为空")
    @Digits(integer = 8, fraction = 4, message = "机台锚点Y坐标最多保留4位小数")
    private BigDecimal anchorY;

    @NotNull(message = "机台朝向角不能为空")
    @DecimalMin(value = "-180", message = "机台朝向角不能小于-180度")
    @DecimalMax(value = "180", message = "机台朝向角不能大于180度")
    private BigDecimal anchorYaw;
}
