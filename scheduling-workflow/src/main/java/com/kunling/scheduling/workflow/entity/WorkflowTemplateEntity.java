package com.kunling.scheduling.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("workflow_template")
public class WorkflowTemplateEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateNumber;
    private String templateName;
    private String applicableObject;
    private String bpmnXml;
    private String editorData;
    private String deploymentId;
    private String processDefinitionId;
    private Integer deployedVersion;
    private String publishStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
