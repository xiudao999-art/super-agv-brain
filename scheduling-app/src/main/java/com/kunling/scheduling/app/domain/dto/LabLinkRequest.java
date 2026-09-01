package com.kunling.scheduling.app.domain.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

@Data
public class LabLinkRequest {

    @NotBlank(message = "连接编码不能为空")
    @Size(max = 64, message = "连接编码不能超过64个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "连接编码格式不正确")
    private String code;

    @NotNull(message = "连接起点不能为空")
    private Long startNodeId;

    @NotNull(message = "连接终点不能为空")
    private Long endNodeId;

    @NotBlank(message = "连接方向不能为空")
    @Pattern(regexp = "ONE_WAY|BIDIRECTIONAL", message = "连接方向只能是ONE_WAY或BIDIRECTIONAL")
    private String direction;

    @NotNull(message = "限速不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "限速必须大于0")
    @Digits(integer = 5, fraction = 3, message = "限速最多保留3位小数")
    private BigDecimal speedLimit;
}
