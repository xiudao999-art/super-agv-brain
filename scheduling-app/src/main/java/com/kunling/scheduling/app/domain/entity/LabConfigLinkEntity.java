package com.kunling.scheduling.app.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("lab_config_link")
public class LabConfigLinkEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long configId;

    private String code;

    private Long startObjectId;

    private Long endObjectId;

    private String direction;

    private BigDecimal speedLimit;
}
