package com.kunling.scheduling.app.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class FlowTemplateListItem {

    private Long id;

    private String templateNumber;

    private String templateName;

    private Integer status;

    private String applicableScope;

    private Integer nodeCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
