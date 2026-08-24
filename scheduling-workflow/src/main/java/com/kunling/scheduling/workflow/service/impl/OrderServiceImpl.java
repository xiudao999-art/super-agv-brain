package com.kunling.scheduling.workflow.service.impl;

import com.kunling.scheduling.workflow.service.FlowControlService;
import com.kunling.scheduling.workflow.service.OrderService;
import com.kunling.scheduling.workflow.dto.FlowStartRequest;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class OrderServiceImpl implements OrderService {
    @Resource
    private FlowControlService flowControlService;


    @Override
    public boolean createOrder(String processDefinitionId, Long businessKey, Long template) {
        FlowStartRequest request = new FlowStartRequest();
        request.setProcessDefinitionId(processDefinitionId);
        request.setBusinessKey(businessKey);
        request.setTemplateId(template);
        return flowControlService.start(request);
    }
}
