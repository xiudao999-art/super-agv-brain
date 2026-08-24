package com.kunling.scheduling.workflow.service;

import com.kunling.scheduling.workflow.dto.FlowTemplateCreateRequest;

public interface FlowTemplateService {
    Long createTemplate(FlowTemplateCreateRequest request);

}
