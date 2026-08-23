package com.kunling.scheduling.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kunling.scheduling.agvflow.domain.dto.*;

import com.kunling.scheduling.agvflow.domain.entity.FlowNode;
import com.kunling.scheduling.agvflow.domain.entity.FlowTemplate;
import com.kunling.scheduling.agvflow.enums.FlowState;
import com.kunling.scheduling.agvflow.enums.NodeState;

import com.kunling.scheduling.agvflow.service.FlowNodeService;

import com.kunling.scheduling.agvflow.service.FlowTemplateService;
import com.kunling.scheduling.workflow.entity.Flow;
import com.kunling.scheduling.workflow.enums.NodeStateEnum;
import com.kunling.scheduling.workflow.mapper.FlowMapper;
import com.kunling.scheduling.workflow.service.FlowService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class FlowServiceImpl extends ServiceImpl<FlowMapper, Flow> implements FlowService {

    @Override
    public boolean processCallback(String executionId, String taskId, String businessKey) {
        return false;
    }
}
