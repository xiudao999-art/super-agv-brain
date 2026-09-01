package com.kunling.scheduling.app.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("lab_config_object")
public class LabConfigObjectEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long configId;

    private Long parentId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long locationId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long navObjectId;

    private String code;

    private String name;

    private String kind;

    private String type;

    private String coordinateFrame;

    private BigDecimal x;

    private BigDecimal y;

    private BigDecimal z;

    private BigDecimal rx;

    private BigDecimal ry;

    private BigDecimal rz;
}
