package com.kunling.scheduling.agvflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.kunling.scheduling.agvflow.domain.entity.FlowNode;
import com.kunling.scheduling.agvflow.mapper.FlowNodeMapper;
import com.kunling.scheduling.agvflow.service.FlowNodeService;
import org.springframework.stereotype.Service;

@Service
public class FlowNodeServiceImpl extends ServiceImpl<FlowNodeMapper, FlowNode>
        implements FlowNodeService {
}
