package com.kunling.scheduling.agvflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
@Schema(description = "新增实验室空间并创建首个草稿")
public class CreateLabSpaceRequest {

    @NotBlank(message = "空间编码不能为空")
    @Size(max = 64, message = "空间编码不能超过64个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "空间编码只能包含字母、数字、下划线和短横线")
    private String code;

    @NotBlank(message = "空间名称不能为空")
    @Size(max = 128, message = "空间名称不能超过128个字符")
    private String name;

    @Valid
    @NotNull(message = "地图信息不能为空")
    private LabMapRequest map;
}
