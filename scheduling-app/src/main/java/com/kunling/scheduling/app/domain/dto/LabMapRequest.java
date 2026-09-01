package com.kunling.scheduling.app.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@Schema(description = "实验室地图信息")
public class LabMapRequest {

    @NotBlank(message = "地图名称不能为空")
    @Size(max = 128, message = "地图名称不能超过128个字符")
    private String name;

    @NotBlank(message = "地图版本不能为空")
    @Size(max = 64, message = "地图版本不能超过64个字符")
    private String version;

    @NotBlank(message = "地图图片地址不能为空")
    @Size(max = 512, message = "地图图片地址不能超过512个字符")
    @Schema(description = "图片上传接口返回的相对地址", example = "/files/550e8400-e29b-41d4-a716-446655440000.png")
    private String imageUrl;
}
