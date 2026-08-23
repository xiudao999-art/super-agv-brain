package com.kunling.scheduling.workflow.service.impl;

import com.kunling.scheduling.workflow.service.FlowControlService;
import com.kunling.scheduling.workflow.service.OrderService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class OrderServiceImpl implements OrderService {
    @Resource
    private FlowControlService flowControlService;


    @Override
    public boolean createOrder(String processDefinitionId, Long businessKey, Long template) {
        flowControlService.start(processDefinitionId, 123545566L, template);
        return false;
    }
}
