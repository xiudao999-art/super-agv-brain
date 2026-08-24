package com.kunling.scheduling.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.kunling.scheduling.workflow.entity.Flow;
import com.kunling.scheduling.workflow.mapper.FlowMapper;
import com.kunling.scheduling.workflow.service.FlowService;
import com.kunling.scheduling.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class FlowServiceImpl extends ServiceImpl<FlowMapper, Flow> implements FlowService {

    @Resource
    private WorkflowService workflowService;


}
