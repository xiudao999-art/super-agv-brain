package com.kunling.scheduling.agvflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
@Schema(description = "初始化唯一实验室及其首个配置草稿")
public class InitializeLabRequest {

    @NotBlank(message = "实验室名称不能为空")
    @Size(max = 128, message = "实验室名称不能超过128个字符")
    private String name;

    @Valid
    @NotNull(message = "地图信息不能为空")
    private LabMapRequest map;
}
