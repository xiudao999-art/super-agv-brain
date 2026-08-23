package com.kunling.scheduling.agvflow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@Schema(description = "修改实验室空间名称")
public class UpdateLabSpaceRequest {

    @NotBlank(message = "空间名称不能为空")
    @Size(max = 128, message = "空间名称不能超过128个字符")
    private String name;
}
