package com.kunling.scheduling.agvflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@Schema(description = "外部地图引用")
public class LabMapRequest {

    @NotBlank(message = "地图名称不能为空")
    @Size(max = 128, message = "地图名称不能超过128个字符")
    private String name;

    @NotBlank(message = "地图版本不能为空")
    @Size(max = 64, message = "地图版本不能超过64个字符")
    private String version;

    @NotBlank(message = "地图文件引用不能为空")
    @Size(max = 512, message = "地图文件引用不能超过512个字符")
    private String fileRef;
}
