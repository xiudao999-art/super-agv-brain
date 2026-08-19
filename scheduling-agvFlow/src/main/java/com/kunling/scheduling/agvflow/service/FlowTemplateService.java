package com.kunling.scheduling.agvflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kunling.scheduling.agvflow.domain.dto.FlowTemplateCreateRequest;
import com.kunling.scheduling.agvflow.domain.dto.FlowTemplateDetail;
import com.kunling.scheduling.agvflow.domain.entity.FlowTemplate;


public interface FlowTemplateService extends IService<FlowTemplate> {
    Integer createTemplate(FlowTemplateCreateRequest request);

    FlowTemplateDetail getTemplateDetail(Integer id);
}
