package com.kunling.scheduling.agvflow.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;
import com.kunling.scheduling.agvflow.enums.NodeState;
import com.kunling.scheduling.agvflow.enums.FlowState;

@Data
public class FlowDetail {

    private Long id;

    private String flowName;

    private String orderNumber;

    private Long templateId;

    private String templateName;

    private Integer templateVersion;

    private FlowState flowState;

    private Long currentNodeId;

    private String currentNodeName;

    private NodeState currentNodeState;

    private Integer nodeCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    private Integer version;

    private Long lastEventId;

    private String errorCode;

    private String errorMessage;

    private Integer attempt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
