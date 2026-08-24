package com.kunling.scheduling.workflow.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.workflow.dto.WorkflowRequests;
import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.dto.WorkflowTemplateRequests;
import com.kunling.scheduling.workflow.dto.WorkflowTemplateResponses;
import com.kunling.scheduling.workflow.entity.WorkflowTemplateEntity;
import com.kunling.scheduling.workflow.entity.Flow;
import com.kunling.scheduling.workflow.mapper.WorkflowTemplateMapper;
import com.kunling.scheduling.workflow.service.FlowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Service
public class WorkflowTemplateService {
    private final WorkflowTemplateMapper mapper;
    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper;
    private final FlowService flowService;

    public WorkflowTemplateService(WorkflowTemplateMapper mapper, WorkflowService workflowService,
                                   ObjectMapper objectMapper, FlowService flowService) {
        this.mapper = mapper;
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
        this.flowService = flowService;
    }

    @Transactional
    public WorkflowTemplateResponses.Detail create(WorkflowTemplateRequests.Save request) {
        WorkflowTemplateEntity entity = new WorkflowTemplateEntity();
        entity.setTemplateNumber("WFT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss")));
        copy(request, entity);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        mapper.insert(entity);
        return detail(entity);
    }

    @Transactional
    public WorkflowTemplateResponses.Detail update(Long id, WorkflowTemplateRequests.Save request) {
        WorkflowTemplateEntity entity = require(id);
        copy(request, entity);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        return detail(entity);
    }

    public WorkflowTemplateResponses.Detail get(Long id) { return detail(require(id)); }

    public List<WorkflowTemplateResponses.Summary> list(String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        return mapper.selectList(Wrappers.<WorkflowTemplateEntity>lambdaQuery()
                .and(!value.isEmpty(), q -> q.like(WorkflowTemplateEntity::getTemplateName, value)
                        .or().like(WorkflowTemplateEntity::getTemplateNumber, value))
                .orderByDesc(WorkflowTemplateEntity::getId)).stream().map(this::summary).collect(Collectors.toList());
    }

    /** 查询模板列表页，动作顺序根据BPMN sequenceFlow连线生成。 */
    public WorkflowTemplateResponses.Page page(long pageNum, long pageSize, String keyword) {
        if (pageNum < 1) throw new IllegalArgumentException("pageNum不能小于1");
        if (pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("pageSize范围必须为1到200");
        String value = keyword == null ? "" : keyword.trim();
        Page<WorkflowTemplateEntity> result = mapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<WorkflowTemplateEntity>lambdaQuery()
                        .and(!value.isEmpty(), q -> q.like(WorkflowTemplateEntity::getTemplateName, value)
                                .or().like(WorkflowTemplateEntity::getTemplateNumber, value)
                                .or().like(WorkflowTemplateEntity::getApplicableObject, value))
                        .orderByDesc(WorkflowTemplateEntity::getId));
        List<WorkflowTemplateResponses.PageItem> records = result.getRecords().stream()
                .map(this::pageItem).collect(Collectors.toList());
        return new WorkflowTemplateResponses.Page(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /** 查询“流程列表”页，流程通过template_id引用workflow_template。 */
    public WorkflowTemplateResponses.FlowPage flowPage(long pageNum, long pageSize, String keyword) {
        if (pageNum < 1) throw new IllegalArgumentException("pageNum不能小于1");
        if (pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("pageSize范围必须为1到200");

        String value = keyword == null ? "" : keyword.trim();
        List<Long> matchingTemplateIds = Collections.emptyList();
        if (!value.isEmpty()) {
            matchingTemplateIds = mapper.selectList(Wrappers.<WorkflowTemplateEntity>lambdaQuery()
                            .like(WorkflowTemplateEntity::getTemplateName, value))
                    .stream().map(WorkflowTemplateEntity::getId).collect(Collectors.toList());
        }

        final List<Long> templateIdsForSearch = matchingTemplateIds;
        Page<Flow> flowResult = flowService.page(new Page<>(pageNum, pageSize),
                Wrappers.<Flow>lambdaQuery()
                        .and(!value.isEmpty(), query -> {
                            query.like(Flow::getFlowName, value);
                            if (!templateIdsForSearch.isEmpty()) {
                                query.or().in(Flow::getTemplateId, templateIdsForSearch);
                            }
                        })
                        .orderByDesc(Flow::getUpdateTime)
                        .orderByDesc(Flow::getId));

        Set<Long> templateIds = flowResult.getRecords().stream().map(Flow::getTemplateId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, WorkflowTemplateEntity> templates = templateIds.isEmpty()
                ? Collections.emptyMap()
                : mapper.selectBatchIds(templateIds).stream()
                        .collect(Collectors.toMap(WorkflowTemplateEntity::getId, item -> item));

        List<WorkflowTemplateResponses.FlowPageItem> records = flowResult.getRecords().stream()
                .map(flow -> flowPageItem(flow, templates.get(flow.getTemplateId())))
                .collect(Collectors.toList());
        return new WorkflowTemplateResponses.FlowPage(
                flowResult.getTotal(), flowResult.getCurrent(), flowResult.getSize(), records);
    }

    @Transactional
    public void delete(Long id) { require(id); mapper.deleteById(id); }

    @Transactional
    public WorkflowResponses.Definition deploy(Long id) {
        WorkflowTemplateEntity entity = require(id);
        WorkflowRequests.DeployDefinition request = new WorkflowRequests.DeployDefinition();
        request.setName(entity.getTemplateName());
        request.setCategory("AGV_TEMPLATE");
        request.setResourceName(entity.getTemplateNumber() + ".bpmn20.xml");
        request.setBpmnXml(entity.getBpmnXml());
        WorkflowResponses.Definition definition = workflowService.deploy(request);
        entity.setDeploymentId(definition.getDeploymentId());
        entity.setProcessDefinitionId(definition.getId());
        entity.setDeployedVersion(definition.getVersion());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
        return definition;
    }

    private WorkflowTemplateEntity require(Long id) {
        WorkflowTemplateEntity entity = mapper.selectById(id);
        if (entity == null) throw new NoSuchElementException("流程模板不存在: " + id);
        return entity;
    }

    private void copy(WorkflowTemplateRequests.Save request, WorkflowTemplateEntity entity) {
        entity.setTemplateName(request.getTemplateName().trim());
        entity.setApplicableObject(request.getApplicableObject());
        entity.setBpmnXml(request.getBpmnXml());
        entity.setEditorData(writeJson(request.getEditorData()));
    }

    private WorkflowTemplateResponses.Detail detail(WorkflowTemplateEntity value) {
        return new WorkflowTemplateResponses.Detail(value.getId(), value.getTemplateNumber(), value.getTemplateName(),
                value.getApplicableObject(), value.getBpmnXml(), readJson(value.getEditorData()), value.getDeploymentId(),
                value.getProcessDefinitionId(), value.getDeployedVersion(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private WorkflowTemplateResponses.Summary summary(WorkflowTemplateEntity value) {
        return new WorkflowTemplateResponses.Summary(value.getId(), value.getTemplateNumber(), value.getTemplateName(),
                value.getApplicableObject(), value.getProcessDefinitionId(), value.getDeployedVersion(), value.getUpdatedAt());
    }

    private WorkflowTemplateResponses.PageItem pageItem(WorkflowTemplateEntity value) {
        List<String> sequence = parseMainActionSequence(value.getBpmnXml());
        boolean deployed = StringUtils.isNotBlank(value.getProcessDefinitionId());
        return new WorkflowTemplateResponses.PageItem(value.getId(), value.getTemplateNumber(), value.getTemplateName(),
                sequence, String.join(" → ", sequence), value.getApplicableObject(),
                value.getDeployedVersion(), deployed ? "ENABLED" : "DRAFT", deployed ? "已启用" : "草稿",
                value.getProcessDefinitionId(), value.getUpdatedAt());
    }

    private WorkflowTemplateResponses.FlowPageItem flowPageItem(
            Flow flow, WorkflowTemplateEntity template) {
        String displayNumber = "FLOW-" + flow.getId();
        String templateName = template == null ? null : template.getTemplateName();
        Integer nodeCount = template == null ? 0 : parseMainActionSequence(template.getBpmnXml()).size();
//        String processDefinitionId = StringUtils.defaultIfBlank(flow.getProcessDefinitionId(),
//                template == null ? null : template.getProcessDefinitionId());
        return new WorkflowTemplateResponses.FlowPageItem(
                flow.getId(), displayNumber, flow.getFlowName(), flow.getTemplateId(), templateName,
                nodeCount, flow.getUpdateTime());
    }

    /**
     * 只解析process的直接子节点。subProcess在模板列表里作为一个主节点展示，
     * 其内部动作仍由模板详情接口的editorData/BPMN XML负责回显。
     */
    private List<String> parseMainActionSequence(String bpmnXml) {
        if (StringUtils.isBlank(bpmnXml)) return Collections.emptyList();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
            NodeList processes = document.getElementsByTagNameNS("*", "process");
            if (processes.getLength() == 0) return Collections.emptyList();

            Element process = (Element) processes.item(0);
            Map<String, String> nodeNames = new LinkedHashMap<>();
            Map<String, List<String>> outgoing = new HashMap<>();
            List<String> starts = new ArrayList<>();
            NodeList children = process.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE) continue;
                Element element = (Element) child;
                String type = element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
                if ("sequenceFlow".equals(type)) {
                    outgoing.computeIfAbsent(element.getAttribute("sourceRef"), key -> new ArrayList<>())
                            .add(element.getAttribute("targetRef"));
                    continue;
                }
                String id = element.getAttribute("id");
                if (StringUtils.isBlank(id) || isNonActionElement(type)) continue;
                String name = StringUtils.defaultIfBlank(element.getAttribute("name"), id);
                nodeNames.put(id, name);
                if ("startEvent".equals(type)) starts.add(id);
            }

            List<String> result = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            for (String start : starts) appendSequence(start, nodeNames, outgoing, visited, result);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("模板BPMN XML无法解析", e);
        }
    }

    private void appendSequence(String nodeId, Map<String, String> nodeNames,
                                Map<String, List<String>> outgoing, Set<String> visited, List<String> result) {
        if (!visited.add(nodeId)) return;
        String name = nodeNames.get(nodeId);
        if (name != null) result.add(name);
        for (String target : outgoing.getOrDefault(nodeId, Collections.emptyList())) {
            appendSequence(target, nodeNames, outgoing, visited, result);
        }
    }

    private boolean isNonActionElement(String type) {
        return "documentation".equals(type) || "extensionElements".equals(type)
                || "laneSet".equals(type) || "dataObjectReference".equals(type)
                || "textAnnotation".equals(type) || "association".equals(type);
    }

    private String writeJson(JsonNode value) {
        if (value == null || value.isNull()) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("editorData格式错误", e); }
    }

    private JsonNode readJson(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("editorData无法解析", e); }
    }
}
