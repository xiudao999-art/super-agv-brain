package com.kunling.scheduling.agvflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kunling.scheduling.agvflow.domain.dto.*;
import com.kunling.scheduling.agvflow.domain.entity.FlowTemplate;

public interface FlowTemplateService extends IService<FlowTemplate> {

    Long createTemplate(FlowTemplateCreateRequest request);

    FlowTemplateDetail getTemplateDetail(Long id);

    Page<FlowTemplateListItem> pageTemplates(int current, int size, String keyword);

    FlowTemplateDetail updateTemplate(Long id, FlowTemplateUpdateRequest request);

    void deleteTemplate(Long id);

    void startFlowNode(Long tempId);

    void startFlow(Long flowId);

    Long skipHangNodeAndStartNext();
}
