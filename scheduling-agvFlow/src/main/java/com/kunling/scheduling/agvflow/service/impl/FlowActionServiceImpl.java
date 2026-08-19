package com.kunling.scheduling.agvflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kunling.scheduling.agvflow.domain.entity.FlowAction;
import com.kunling.scheduling.agvflow.mapper.FlowActionMapper;
import com.kunling.scheduling.agvflow.service.FlowActionService;
import org.springframework.stereotype.Service;

@Service
public class FlowActionServiceImpl extends ServiceImpl<FlowActionMapper, FlowAction>
        implements FlowActionService {
}
