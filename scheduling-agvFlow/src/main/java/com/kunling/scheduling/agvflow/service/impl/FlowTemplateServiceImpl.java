package com.kunling.scheduling.agvflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.commissioning.application.ActionParameterSetService;
import com.kunling.scheduling.action.commissioning.application.ActionParameterSetView;
import com.kunling.scheduling.action.definition.application.ActionDefinitionService;
import com.kunling.scheduling.action.definition.application.ActionDefinitionView;
import com.kunling.scheduling.action.execution.application.ExecuteActionCommand;
import com.kunling.scheduling.action.execution.application.StartActionExecutionRequest;
import com.kunling.scheduling.agvflow.action.AgvFlowActionGateway;
import com.kunling.scheduling.agvflow.action.AgvFlowExecutionsGateway;
import com.kunling.scheduling.agvflow.domain.dto.FlowTemplateCreateRequest;
import com.kunling.scheduling.agvflow.domain.dto.FlowTemplateDetail;
import com.kunling.scheduling.agvflow.domain.dto.*;
import com.kunling.scheduling.agvflow.domain.entity.FlowAction;
import com.kunling.scheduling.agvflow.domain.entity.FlowNode;
import com.kunling.scheduling.agvflow.domain.entity.FlowTemplate;
import com.kunling.scheduling.agvflow.enums.NodeState;
import com.kunling.scheduling.agvflow.mapper.FlowNodeMapper;
import com.kunling.scheduling.agvflow.mapper.FlowTemplateMapper;
import com.kunling.scheduling.agvflow.service.FlowActionService;
import com.kunling.scheduling.agvflow.service.FlowNodeService;
import com.kunling.scheduling.agvflow.service.FlowTemplateService;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.kunling.scheduling.agvflow.enums.NodeState.RUNNING;

@Service
@Slf4j
public class FlowTemplateServiceImpl extends ServiceImpl<FlowTemplateMapper, FlowTemplate>
        implements FlowTemplateService {

    @Resource
    private FlowNodeService nodeService;

    @Resource
    private FlowActionService actionService;

    @Resource
    private AgvFlowActionGateway actionGateway;

    @Resource
    private AgvFlowExecutionsGateway executionsGateway;


    @Resource
    private ObjectMapper objectMapper;
    @Autowired
    private FlowNodeMapper flowNodeMapper;


    /**
     * 创建流程模板
     *
     * @param request 模板创建请求对象，包含模板名称、状态、适用对象及节点列表
     * @return 新建模板的自增主键ID
     */
    @Override
    @Transactional
    public Long createTemplate(FlowTemplateCreateRequest request) {
        // 自动生成模板编号：FT-{年份}{3位序号}
        String templateNumber = generateTemplateNumber();

        // 保存模板主表
        FlowTemplate template = new FlowTemplate();
        template.setTemplateNumber(templateNumber);
        template.setTemplateName(request.getTemplateName());
        template.setStatus(request.getStatus());
        template.setVersion(request.getVersion());
        template.setApplicableScope(request.getApplicableScope());
        save(template);
        // 循环保存节点及其动作（动作编码全局复用，避免重复创建）
        validateCreateNodes(request.getNodes());
        if (request.getNodes() != null) {
            for (FlowTemplateCreateRequest.NodeRequest nodeRequest : request.getNodes()) {
                FlowNode node = new FlowNode();
                node.setTemplateId(template.getId());
                node.setNodeName(nodeRequest.getNodeName());
                node.setNodeCode(nodeRequest.getNodeCode());
                node.setSort(nodeRequest.getSort());
                node.setNodeCategory(nodeRequest.getNodeCategory());
                node.setFailureStrategy(nodeRequest.getFailureStrategy());
                node.setCompletionCriteria(nodeRequest.getCompletionCriteria());
                node.setLeftNodeId(nodeRequest.getLeftNodeId());
                node.setRightNodeId(nodeRequest.getRightNodeId());
                nodeService.save(node);

                Map<Long, Integer> actionSortById = new HashMap<>();
                if (!CollectionUtils.isEmpty(nodeRequest.getChildNode())) {
                    for (FlowTemplateCreateRequest.ChildNode childNode : nodeRequest.getChildNode()) {
                        Long orCreateAction = createAction(childNode, node.getId());
                        actionSortById.put(orCreateAction, childNode.getSort());
                    }
                }
                //拼接子节点id
                //优先比较value
                //value相同，按key升序
                List<Long> childNodeList = actionSortById.entrySet().stream()
                        .sorted(Comparator.comparingInt((Map.Entry<Long, Integer> e) -> e.getValue())
                                .thenComparingLong(Map.Entry::getKey))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
                node.setActions(childNodeList);
                linkActionsInOrder(childNodeList);
                nodeService.updateById(node);
            }
        }
        return template.getId();
    }

    /**
     * 生成模板编号：格式 FT-{年份}{3位序号}
     *
     * @return 模板编号字符串
     */
    private String generateTemplateNumber() {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        int seq = Math.toIntExact(count() + 1);
        return String.format("FT-%d%03d", year, seq);
    }

    /**
     * 根据动作编码查找已有动作；不存在则新建并返回动作ID
     *
     * @param request 动作创建请求对象，包含动作名称、动作编码、节点分类、适用对象等
     * @return 动作主键ID
     */
    private Long createAction(FlowTemplateCreateRequest.ChildNode request, Long parentNodeId) {
        FlowAction action = new FlowAction();
        action.setActionName(request.getActionName());
        action.setActionCode(StringUtils.isNotBlank(request.getActionCode())
                ? request.getActionCode() : "ACTION-" + UUID.randomUUID().toString().replace("-", ""));
        action.setMachineId(parentNodeId);
        action.setNodeCategory("CHILD_NODE");
        action.setFailureStrategy(request.getFailureStrategy());
        action.setCompletionCriteria(request.getCompletionCriteria());
        action.setNextActionId(request.getNextActionId());
        actionService.save(action);
        return action.getId();
    }

    private void linkActionsInOrder(List<Long> actionIds) {
        for (int index = 0; index < actionIds.size(); index++) {
            FlowAction action = actionService.getById(actionIds.get(index));
            action.setNextActionId(index + 1 < actionIds.size() ? actionIds.get(index + 1) : null);
            actionService.updateById(action);
        }
    }

    private void validateCreateNodes(List<FlowTemplateCreateRequest.NodeRequest> nodes) {
        if (CollectionUtils.isEmpty(nodes)) {
            throw new IllegalArgumentException("主流程至少需要开始和结束节点");
        }
        List<FlowTemplateCreateRequest.NodeRequest> sorted = nodes.stream()
                .sorted(Comparator.comparing(FlowTemplateCreateRequest.NodeRequest::getSort))
                .collect(Collectors.toList());
        if (!"START".equalsIgnoreCase(sorted.get(0).getNodeCode())
                || !"END".equalsIgnoreCase(sorted.get(sorted.size() - 1).getNodeCode())) {
            throw new IllegalArgumentException("主流程必须从 START 节点进入并以 END 节点收尾");
        }
    }

    /**
     * 查询单个模板详情（含节点与动作）
     *
     * @param id 模板主键ID
     * @return 模板详情对象
     */
    @Override
    public FlowTemplateDetail getTemplateDetail(Long id) {
        FlowTemplate template = getById(id);
        if (template == null) {
            throw new NoSuchElementException("流程模板不存在: " + id);
        }
        return toTemplateDetail(template);
    }

    /**
     * 分页查询模板列表
     *
     * @param current 当前页码（从1开始）
     * @param size    每页条数
     * @param keyword 关键字（可选，匹配模板编号或模板名称）
     * @return 分页结果，每项为一个模板列表项
     */
    @Override
    public Page<FlowTemplateListItem> pageTemplates(int current, int size, String keyword) {
        Page<FlowTemplate> page = lambdaQuery()
                .and(StringUtils.isNotEmpty(keyword), wrapper -> wrapper
                        .like(FlowTemplate::getTemplateNumber, keyword)
                        .or()
                        .like(FlowTemplate::getTemplateName, keyword))
                .orderByDesc(FlowTemplate::getUpdateTime)
                .page(new Page<>(current, size));

        List<FlowTemplate> templates = page.getRecords();
        if (templates.isEmpty()) {
            return new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        }

        // 批量查询节点数量，避免 N+1
        List<Long> templateIds = templates.stream()
                .map(FlowTemplate::getId)
                .collect(Collectors.toList());

        Map<Long, Long> nodeCountMap = nodeService.list(Wrappers.<FlowNode>lambdaQuery()
                        .in(FlowNode::getTemplateId, templateIds))
                .stream()
                .collect(Collectors.groupingBy(FlowNode::getTemplateId, Collectors.counting()));

        List<FlowTemplateListItem> items = templates.stream()
                .map(t -> toTemplateListItem(t, nodeCountMap))
                .collect(Collectors.toList());

        Page<FlowTemplateListItem> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(items);
        return result;
    }

    /**
     * 编辑模板：更新模板基本信息，并对节点做增量增删改
     *
     * @param id      模板主键ID
     * @param request 模板更新请求对象，包含模板名称、状态、适用对象及节点列表
     * @return 更新后的模板详情
     */
    @Override
    @Transactional
    public FlowTemplateDetail updateTemplate(Long id, FlowTemplateUpdateRequest request) {
        FlowTemplate template = getById(id);
        if (template == null) {
            throw new NoSuchElementException("流程模板不存在: " + id);
        }

        // 更新模板主表
        template.setTemplateName(request.getTemplateName());
        template.setStatus(request.getStatus());
        template.setVersion(request.getVersion());
        template.setApplicableScope(request.getApplicableScope());
        updateById(template);

        // 节点级联增删改
        if (request.getNodes() != null) {
            // 现有节点列表
            List<FlowNode> existingNodes = nodeService.list(Wrappers.<FlowNode>lambdaQuery()
                    .eq(FlowNode::getTemplateId, id));

            // 本次提交中已处理的节点ID集合（用于后续删除未提交节点）
            Set<Long> incomingNodeIds = new HashSet<>();
            // 动作编码到动作ID的缓存（避免重复创建动作）
            Map<String, Long> actionIdByCode = new HashMap<>();

            for (FlowTemplateUpdateRequest.NodeRequest nodeRequest : request.getNodes()) {
                FlowNode node;
                if (nodeRequest.getId() != null) {
                    // 更新已有节点
                    node = existingNodes.stream()
                            .filter(n -> n.getId().equals(nodeRequest.getId()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + nodeRequest.getId()));
                    node.setNodeName(nodeRequest.getNodeName());
                    node.setNodeCode(nodeRequest.getNodeCode());
                    node.setSort(nodeRequest.getSort());
                    node.setNodeCategory(nodeRequest.getNodeCategory());
                    node.setFailureStrategy(nodeRequest.getFailureStrategy());
                    node.setParentNodeId(nodeRequest.getParentNodeId());
                    node.setCompletionCriteria(nodeRequest.getCompletionCriteria());
                    node.setLeftNodeId(nodeRequest.getLeftNodeId());
                    node.setRightNodeId(nodeRequest.getRightNodeId());
                } else {
                    // 新增节点
                    node = new FlowNode();
                    node.setTemplateId(id);
                    node.setNodeName(nodeRequest.getNodeName());
                    node.setNodeCode(nodeRequest.getNodeCode());
                    node.setSort(nodeRequest.getSort());
                    node.setNodeCategory(nodeRequest.getNodeCategory());
                    node.setFailureStrategy(nodeRequest.getFailureStrategy());
                    node.setParentNodeId(nodeRequest.getParentNodeId());
                    node.setCompletionCriteria(nodeRequest.getCompletionCriteria());
                    node.setLeftNodeId(nodeRequest.getLeftNodeId());
                    node.setRightNodeId(nodeRequest.getRightNodeId());
                }

                // 收集本节点关联的动作ID
                List<Long> actionIds = new ArrayList<>();
                if (nodeRequest.getActions() != null) {
                    for (FlowTemplateUpdateRequest.ActionRequest actionRequest : nodeRequest.getActions()) {
                        if (actionRequest.getId() != null) {
                            // 已有动作直接引用
                            actionIds.add(actionRequest.getId());
                        } else {
                            // 新动作按编码查找或创建
                            Long actionId = actionIdByCode.computeIfAbsent(
                                    actionRequest.getActionCode(),
                                    ignored -> findOrCreateActionFromUpdate(actionRequest)
                            );
                            actionIds.add(actionId);
                        }
                    }
                }
                node.setActions(actionIds);

                if (node.getId() == null) {
                    nodeService.save(node);
                } else {
                    nodeService.updateById(node);
                }
                incomingNodeIds.add(node.getId());
            }

            // 删除本次未提交的节点
            for (FlowNode existingNode : existingNodes) {
                if (!incomingNodeIds.contains(existingNode.getId())) {
                    nodeService.removeById(existingNode.getId());
                }
            }
        }

        return toTemplateDetail(template);
    }

    /**
     * 编辑场景下根据动作编码查找已有动作；不存在则新建并返回动作ID
     *
     * @param request 动作更新请求对象，包含动作名称、动作编码、节点分类、适用对象等
     * @return 动作主键ID
     */
    private Long findOrCreateActionFromUpdate(FlowTemplateUpdateRequest.ActionRequest request) {
        FlowAction existing = actionService.getOne(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getActionCode, request.getActionCode()), false);
        if (existing != null) {
            return existing.getId();
        }
        FlowAction action = new FlowAction();
        action.setMachineId(request.getMachineId());
        action.setActionName(request.getActionName());
        action.setActionCode(request.getActionCode());
        action.setNodeCategory(request.getNodeCategory());
        action.setApplicableScope(request.getApplicableScope());
        action.setFailureStrategy(request.getFailureStrategy());
        action.setCompletionCriteria(request.getCompletionCriteria());
        action.setNextActionId(request.getNextActionId());
        actionService.save(action);
        return action.getId();
    }

    /**
     * 删除模板（同时级联删除其下所有节点）
     *
     * @param id 模板主键ID
     */
    @Override
    @Transactional
    public void deleteTemplate(Long id) {
        FlowTemplate template = getById(id);
        if (template == null) {
            throw new NoSuchElementException("流程模板不存在: " + id);
        }

        nodeService.remove(Wrappers.<FlowNode>lambdaQuery()
                .eq(FlowNode::getTemplateId, id));
        removeById(id);
    }

    /**
     * 将模板实体转换为详情DTO（含节点与动作）
     *
     * @param template 流程模板实体
     * @return 模板详情对象
     */
    private FlowTemplateDetail toTemplateDetail(FlowTemplate template) {
        // 按排序、ID升序查询节点
        List<FlowNode> nodes = nodeService.list(Wrappers.<FlowNode>lambdaQuery()
                .eq(FlowNode::getTemplateId, template.getId())
                .orderByAsc(FlowNode::getSort)
                .orderByAsc(FlowNode::getId));

        // 收集所有节点引用的动作ID并批量查询
        Set<Long> allActionIds = new HashSet<>();
        nodes.forEach(node -> {
            if (node.getActions() != null) {
                allActionIds.addAll(node.getActions());
            }
        });
        Map<Long, FlowAction> actionsById = new HashMap<>();
        if (!allActionIds.isEmpty()) {
            actionService.listByIds(allActionIds)
                    .forEach(action -> actionsById.put(action.getId(), action));
        }

        // 节点转DTO
        List<FlowTemplateDetail.NodeDetail> nodeDetails = nodes.stream()
                .map(node -> toNodeDetail(node, actionsById))
                .collect(Collectors.toList());

        return new FlowTemplateDetail(template.getId(), template.getTemplateNumber(),
                template.getTemplateName(), template.getStatus(), template.getVersion(),
                template.getApplicableScope(), nodeDetails,
                template.getCreateTime(), template.getUpdateTime());
    }

    /**
     * 将节点实体转换为节点详情DTO
     *
     * @param node        流程节点实体
     * @param actionsById 动作ID到动作实体的映射，用于解析本节点引用的动作
     * @return 节点详情对象
     */
    private FlowTemplateDetail.NodeDetail toNodeDetail(
            FlowNode node,
            Map<Long, FlowAction> actionsById
    ) {
        // 通过映射表解析本节点关联的动作详情列表
        List<FlowTemplateDetail.ActionDetail> actions = node.getActions() == null
                ? Collections.emptyList()
                : node.getActions().stream()
                .map(actionsById::get)
                .filter(Objects::nonNull)
                .map(action -> new FlowTemplateDetail.ActionDetail(action.getId(), action.getMachineId(),
                        action.getActionName(), action.getActionCode(),
                        action.getNodeCategory(), action.getApplicableScope(), action.getFailureStrategy(),
                        action.getCompletionCriteria(), action.getNextActionId()))
                .collect(Collectors.toList());
        return new FlowTemplateDetail.NodeDetail(node.getId(), node.getNodeName(), node.getNodeCode(), node.getSort(),
                node.getNodeCategory(), node.getFailureStrategy(),
                node.getParentNodeId(), node.getCompletionCriteria(),
                node.getLeftNodeId(), node.getRightNodeId(),
                actions);
    }

    /**
     * 将模板实体转换为列表项DTO
     *
     * @param template     流程模板实体
     * @param nodeCountMap 模板ID到其下节点数量的映射
     * @return 模板列表项对象
     */
    private FlowTemplateListItem toTemplateListItem(FlowTemplate template,
                                                    Map<Long, Long> nodeCountMap) {
        FlowTemplateListItem item = new FlowTemplateListItem();
        item.setId(template.getId());
        item.setTemplateNumber(template.getTemplateNumber());
        item.setTemplateName(template.getTemplateName());
        item.setStatus(template.getStatus());
        item.setApplicableScope(template.getApplicableScope());
        item.setCreateTime(template.getCreateTime());
        item.setUpdateTime(template.getUpdateTime());

        Long nodeCount = nodeCountMap.get(template.getId());
        item.setNodeCount(nodeCount != null ? nodeCount.intValue() : 0);
        return item;
    }



    @Override
    public void startFlow(Long flowId) {
        FlowTemplate template = baseMapper.selectById(flowId);
        log.info("流程---{}---开始启动", template.getTemplateName());
        template.setStatus(1);
        List<FlowNode> list = nodeService.lambdaQuery().eq(FlowNode::getTemplateId, template.getId()).eq(FlowNode::getStatus, NodeState.PENDING).orderByAsc(FlowNode::getSort).list();
        startFlowNode(list.get(0).getId());
        baseMapper.updateById(template);
    }

    @Override
    public void startFlowNode(Long nodeId) {
        FlowNode node = nodeService.getById(nodeId);
        List<ActionParameterSetView> actions = actionGateway.actions(node.getNodeCode());

        if (!CollectionUtils.isEmpty(actions)) {
            ActionParameterSetView action = actions.get(0);
            log.info("流程节点----{}--开始进行", node.getNodeName());
            ObjectNode input = objectMapper.createObjectNode();
            input.put("targetPoint", "PICK_STATION_A");
            ExecuteActionCommand command = new ExecuteActionCommand(
                    node.getTemplateId().toString(),                                      // workflowInstanceId
                    node.getId().toString(),                                      // workflowNodeInstanceId
                    UUID.randomUUID().toString(),                                // actionInstanceId
                    "R01",                                    // 实际注册的robotId
                    node.getNodeCode(),
                    action.id()   // MOVE参数集ID
            );
            log.info("节点执行参数为：{}", command.toString());
            executionsGateway.execute(command);
        }
        node.setStatus(RUNNING);
        flowNodeMapper.updateById(node);
    }
}
