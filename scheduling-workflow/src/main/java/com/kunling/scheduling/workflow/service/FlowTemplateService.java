package com.kunling.scheduling.workflow.service;

import com.kunling.scheduling.workflow.dto.FlowTemplateCreateRequest;
import com.kunling.scheduling.workflow.dto.FlowTemplateUpdateRequest;
import com.kunling.scheduling.workflow.dto.WorkflowTemplateResponses;
import com.kunling.scheduling.workflow.entity.FlowTemplate;

import java.util.List;

public interface FlowTemplateService {
    Long createTemplate(FlowTemplateCreateRequest request);

    List<WorkflowTemplateResponses.FlowPageItem> flowTemplateList();

    WorkflowTemplateResponses.FlowDetail getFlow(Long id);

    WorkflowTemplateResponses.FlowDetail updateFlow(Long id, FlowTemplateUpdateRequest request);
}
