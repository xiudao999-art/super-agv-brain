package com.kunling.scheduling.app.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@Schema(description = "修改唯一实验室名称")
public class UpdateLabRequest {

    @NotBlank(message = "实验室名称不能为空")
    @Size(max = 128, message = "实验室名称不能超过128个字符")
    private String name;
}
