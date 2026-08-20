package com.kunling.scheduling.agvflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.kunling.scheduling.agvflow.domain.dto.FlowTemplateCreateRequest;
import com.kunling.scheduling.agvflow.domain.dto.FlowTemplateDetail;
import com.kunling.scheduling.agvflow.domain.entity.FlowAction;
import com.kunling.scheduling.agvflow.domain.entity.FlowNode;
import com.kunling.scheduling.agvflow.domain.entity.FlowTemplate;
import com.kunling.scheduling.agvflow.mapper.FlowTemplateMapper;
import com.kunling.scheduling.agvflow.service.FlowActionService;
import com.kunling.scheduling.agvflow.service.FlowNodeService;
import com.kunling.scheduling.agvflow.service.FlowTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FlowTemplateServiceImpl extends ServiceImpl<FlowTemplateMapper, FlowTemplate>
        implements FlowTemplateService {
    @Resource
    private  FlowNodeService nodeService;
    @Resource
    private  FlowActionService actionService;



    @Override
    @Transactional
    public Integer createTemplate(FlowTemplateCreateRequest request) {
        boolean exists = lambdaQuery()
                .eq(FlowTemplate::getTemplateNumber, request.getTemplateNumber())
                .eq(FlowTemplate::getVersion, request.getVersion())
                .exists();
        if (exists) {
            throw new IllegalArgumentException("相同模板编号和版本已存在");
        }

        Set<String> nodeCodes = new HashSet<>();
        for (FlowTemplateCreateRequest.NodeRequest node : request.getNodes()) {
            if (!nodeCodes.add(node.getNodeCode())) {
                throw new IllegalArgumentException("节点编码重复: " + node.getNodeCode());
            }
        }

        FlowTemplate template = new FlowTemplate();
        template.setTemplateNumber(request.getTemplateNumber());
        template.setTemplateName(request.getTemplateName());
        template.setVersion(request.getVersion());
        template.setStatus(request.getStatus());
        save(template);

        Map<String, Integer> actionIdsByCode = new HashMap<>();
        for (FlowTemplateCreateRequest.NodeRequest nodeRequest : request.getNodes()) {
            List<Integer> actionIds = new ArrayList<>();
            for (FlowTemplateCreateRequest.ActionRequest actionRequest : nodeRequest.getActions()) {
                Integer actionId = actionIdsByCode.computeIfAbsent(
                        actionRequest.getActionCode(),
                        ignored -> findOrCreateAction(actionRequest)
                );
                actionIds.add(actionId);
            }

            FlowNode node = new FlowNode();
            node.setTemplateId(template.getId());
            node.setNodeName(nodeRequest.getNodeName());
            node.setNodeCode(nodeRequest.getNodeCode());
            node.setSort(nodeRequest.getSort());
            node.setActions(actionIds);
            nodeService.save(node);
        }
        return template.getId();
    }

    private Integer findOrCreateAction(FlowTemplateCreateRequest.ActionRequest request) {
        FlowAction existing = actionService.getOne(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getActionCode, request.getActionCode()), false);
        if (existing != null) {
            return existing.getId();
        }
        FlowAction action = new FlowAction();
        action.setMachineId(request.getMachineId());
        action.setActionName(request.getActionName());
        action.setActionCode(request.getActionCode());
        actionService.save(action);
        return action.getId();
    }

    @Override
    public FlowTemplateDetail getTemplateDetail(Integer id) {
        FlowTemplate template = getById(id);
        if (template == null) {
            throw new NoSuchElementException("流程模板不存在: " + id);
        }

        List<FlowNode> nodes = nodeService.list(Wrappers.<FlowNode>lambdaQuery()
                .eq(FlowNode::getTemplateId, id)
                .orderByAsc(FlowNode::getSort)
                .orderByAsc(FlowNode::getId));

        Set<Integer> allActionIds = new HashSet<>();
        nodes.forEach(node -> {
            if (node.getActions() != null) {
                allActionIds.addAll(node.getActions());
            }
        });
        Map<Integer, FlowAction> actionsById = new HashMap<>();
        if (!allActionIds.isEmpty()) {
            actionService.listByIds(allActionIds)
                    .forEach(action -> actionsById.put(action.getId(), action));
        }

        List<FlowTemplateDetail.NodeDetail> nodeDetails = nodes.stream()
                .map(node -> toNodeDetail(node, actionsById))
                .collect(Collectors.toList());
        return new FlowTemplateDetail(template.getId(), template.getTemplateNumber(),
                template.getTemplateName(), template.getVersion(), template.getStatus(), nodeDetails);
    }

    private FlowTemplateDetail.NodeDetail toNodeDetail(
            FlowNode node,
            Map<Integer, FlowAction> actionsById
    ) {
        List<FlowTemplateDetail.ActionDetail> actions = node.getActions() == null
                ? Collections.<FlowTemplateDetail.ActionDetail>emptyList()
                : node.getActions().stream()
                .map(actionsById::get)
                .filter(java.util.Objects::nonNull)
                .map(action -> new FlowTemplateDetail.ActionDetail(action.getId(), action.getMachineId(),
                        action.getActionName(), action.getActionCode()))
                .collect(Collectors.toList());
        return new FlowTemplateDetail.NodeDetail(node.getId(), node.getNodeName(),
                node.getNodeCode(), node.getSort(), actions);
    }
}
