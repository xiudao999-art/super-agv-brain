package com.kunling.scheduling.agvflow.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lab_config")
public class LabConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String spaceId;

    private String spaceCode;

    private String spaceName;

    private String mapName;

    private String mapVersion;

    private String mapFileRef;

    private Integer revision;

    private String status;

    private LocalDateTime publishedAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NOT_NULL)
    private LocalDateTime updatedAt;
}
