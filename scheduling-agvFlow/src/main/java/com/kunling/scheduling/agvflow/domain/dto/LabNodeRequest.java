package com.kunling.scheduling.agvflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "通行节点")
public class LabNodeRequest {

    @NotBlank(message = "节点编码不能为空")
    @Size(max = 64, message = "节点编码不能超过64个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "节点编码格式不正确")
    private String code;

    @NotBlank(message = "节点名称不能为空")
    @Size(max = 128, message = "节点名称不能超过128个字符")
    private String name;

    @NotBlank(message = "节点类型不能为空")
    @Size(max = 64, message = "节点类型不能超过64个字符")
    private String type;

    private Long locationId;

    @NotNull(message = "X坐标不能为空")
    @Digits(integer = 8, fraction = 4, message = "X坐标最多保留4位小数")
    private BigDecimal x;

    @NotNull(message = "Y坐标不能为空")
    @Digits(integer = 8, fraction = 4, message = "Y坐标最多保留4位小数")
    private BigDecimal y;

    @NotNull(message = "朝向角不能为空")
    @DecimalMin(value = "-180", message = "朝向角不能小于-180度")
    @DecimalMax(value = "180", message = "朝向角不能大于180度")
    @Digits(integer = 3, fraction = 4, message = "朝向角最多保留4位小数")
    private BigDecimal yaw;
}
