package com.kunling.scheduling.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class BaseEntity  implements Serializable {

    private static final long serialVersionUID = 5055857185348379411L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableLogic
    private Integer isDeleted;


    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NOT_EMPTY)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;


    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE, updateStrategy = FieldStrategy.NOT_EMPTY)
    private Date updateTime;
}
