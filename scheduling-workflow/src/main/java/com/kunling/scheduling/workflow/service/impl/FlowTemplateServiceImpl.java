package com.kunling.scheduling.workflow.service.impl;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.kunling.scheduling.workflow.dto.FlowTemplateCreateRequest;
import com.kunling.scheduling.workflow.dto.FlowTemplateUpdateRequest;
import com.kunling.scheduling.workflow.dto.WorkflowTemplateResponses;
import com.kunling.scheduling.workflow.entity.FlowTemplate;
import com.kunling.scheduling.workflow.mapper.FlowTemplateMapper;
import com.kunling.scheduling.workflow.order.domain.OrderTask;
import com.kunling.scheduling.workflow.order.infrastructure.OrderTaskMapper;
import com.kunling.scheduling.workflow.service.FlowTemplateService;
import com.kunling.scheduling.workflow.service.WorkflowTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;


@Service
@Slf4j
public class FlowTemplateServiceImpl extends ServiceImpl<FlowTemplateMapper, FlowTemplate>
        implements FlowTemplateService {
    @Resource
    private WorkflowTemplateService workflowTemplateService;
    @Resource
    private OrderTaskMapper orderTaskMapper;

    /**
     * 创建流程模板
     *
     * @param request 模板创建请求对象，包含模板名称、状态、适用对象及节点列表
     * @return 新建模板的自增主键ID
     */
    @Override
    @Transactional
    public Long createTemplate(FlowTemplateCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("创建流程参数不能为空");
        }

        WorkflowTemplateResponses.Detail detail = workflowTemplateService.get(request.getSourceTemplateId());
        if (detail == null) {
            throw new IllegalArgumentException("引用的流程模板不存在: " + request.getSourceTemplateId());
        }

        int status = request.getStatus() == null ? 1 : request.getStatus();
        if (status != 0 && status != 1) {
            throw new IllegalArgumentException("启用状态只能是0或1");
        }

        FlowTemplate template = new FlowTemplate();
        template.setTemplateNumber(generateFlowNumber());
        template.setTemplateName(request.getTemplateName().trim());
        template.setSourceTemplateId(detail.getId());
        template.setDescription(trimToNull(request.getDescription()));
        template.setApplicableScope(trimToNull(request.getApplicableScope()));
        template.setStatus(status);
        template.setVersion(1);

        if (!save(template) || template.getId() == null) {
            throw new IllegalStateException("流程保存失败");
        }
        return template.getId();
    }

    @Override
    public List<WorkflowTemplateResponses.FlowPageItem> flowTemplateList() {
        List<FlowTemplate> list = list();
        if (list != null && !list.isEmpty()) {
            return list.stream().map(item -> {
                WorkflowTemplateResponses.FlowPageItem flowPageItem = new WorkflowTemplateResponses.FlowPageItem();
                flowPageItem.setId(item.getId());
                flowPageItem.setFlowName(item.getTemplateName());
                flowPageItem.setFlowNumber(item.getTemplateNumber());
                flowPageItem.setTemplateId(item.getSourceTemplateId());
                flowPageItem.setStatus(item.getStatus());
                flowPageItem.setStatusDescription(statusDescription(item.getStatus()));
                return flowPageItem;
            }).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public WorkflowTemplateResponses.FlowDetail getFlow(Long id) {
        return toDetail(required(id));
    }

    @Override
    @Transactional
    public WorkflowTemplateResponses.FlowDetail updateFlow(Long id, FlowTemplateUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("流程编辑参数不能为空");
        }
        FlowTemplate flow = required(id);
        Long referencedTaskCount = orderTaskMapper.selectCount(Wrappers.<OrderTask>lambdaQuery()
                .eq(OrderTask::getFlowTemplateId, id));
        if (referencedTaskCount != null && referencedTaskCount > 0) {
            throw new IllegalStateException("流程已被订单任务引用，不能修改: " + flow.getTemplateNumber());
        }
//        WorkflowTemplateResponses.Detail template = workflowTemplateService.get(request.getSourceTemplateId());
        validateStatus(request.getStatus());
        flow.setTemplateName(request.getFlowName().trim());
        flow.setSourceTemplateId(request.getSourceTemplateId());
        flow.setStatus(request.getStatus());
        flow.setApplicableScope(trimToNull(request.getApplicableScope()));
        flow.setDescription(trimToNull(request.getDescription()));
        if (!updateById(flow)){
            throw new IllegalStateException("流程编辑保存失败: " + id);
        }
        return toDetail(flow);
    }

    private WorkflowTemplateResponses.FlowDetail toDetail(FlowTemplate flow) {
        WorkflowTemplateResponses.Detail template = workflowTemplateService.get(flow.getSourceTemplateId());
        return new WorkflowTemplateResponses.FlowDetail(flow.getId(), flow.getTemplateNumber(),
                flow.getTemplateName(), flow.getSourceTemplateId(), template.getTemplateName(),
                flow.getStatus(), statusDescription(flow.getStatus()), flow.getApplicableScope(),
                flow.getDescription(), flow.getVersion(), flow.getCreateTime(), flow.getUpdateTime());
    }

    private FlowTemplate required(Long id) {
        FlowTemplate flow = id == null ? null : getById(id);
        if (flow == null) throw new NoSuchElementException("流程不存在: " + id);
        return flow;
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("启用状态只能是0或1");
        }
    }

    private String statusDescription(Integer status) {
        return Integer.valueOf(1).equals(status) ? "启用" : "停用";
    }

    private String generateFlowNumber() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 6).toUpperCase(Locale.ROOT);
        return "FLOW-" + time + "-" + suffix;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
