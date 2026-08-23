package com.kunling.scheduling.workflow.service;

public interface OrderService {
    boolean createOrder (String processDefinitionId,Long businessKey,Long template);
}
