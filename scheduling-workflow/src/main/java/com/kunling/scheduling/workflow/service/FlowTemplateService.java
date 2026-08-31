package com.kunling.scheduling.workflow.service;

import com.kunling.scheduling.workflow.dto.FlowTemplateCreateRequest;
import com.kunling.scheduling.workflow.dto.WorkflowTemplateResponses;
import com.kunling.scheduling.workflow.entity.FlowTemplate;

import java.util.List;

public interface FlowTemplateService {
    Long createTemplate(FlowTemplateCreateRequest request);

    List<WorkflowTemplateResponses.FlowPageItem> flowTemplateList();
}
