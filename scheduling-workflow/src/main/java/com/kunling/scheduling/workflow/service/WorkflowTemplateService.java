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
import com.kunling.scheduling.workflow.entity.FlowTemplate;
import com.kunling.scheduling.workflow.mapper.WorkflowTemplateMapper;
import com.kunling.scheduling.workflow.mapper.FlowTemplateMapper;
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
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
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
    private final FlowTemplateMapper flowTemplateMapper;

    public WorkflowTemplateService(WorkflowTemplateMapper mapper, WorkflowService workflowService,
                                   ObjectMapper objectMapper, FlowTemplateMapper flowTemplateMapper) {
        this.mapper = mapper;
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
        this.flowTemplateMapper = flowTemplateMapper;
    }

    @Transactional
    public WorkflowTemplateResponses.Detail create(WorkflowTemplateRequests.Save request) {
        validateBpmnXml(request.getBpmnXml());
        WorkflowTemplateValidator.validate(request.getBpmnXml(), request.getEditorData());
        WorkflowTemplateEntity entity = new WorkflowTemplateEntity();
        entity.setTemplateNumber("WFT" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss")));
        copy(request, entity);
        entity.setPublishStatus("DRAFT");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        mapper.insert(entity);
        return detail(entity);
    }

    @Transactional
    public WorkflowTemplateResponses.Detail update(WorkflowTemplateRequests.Save request) {
        Long id = request.getId();
        WorkflowTemplateEntity entity = require(id);
        String oldProcessId = processId(entity.getBpmnXml());
        String newProcessId = validateBpmnXml(request.getBpmnXml());
        WorkflowTemplateValidator.validate(request.getBpmnXml(), request.getEditorData());
        if (StringUtils.isNotBlank(entity.getProcessDefinitionId())
                && !java.util.Objects.equals(oldProcessId, newProcessId)) {
            request.setBpmnXml(normalizeProcessId(request.getBpmnXml(), newProcessId, oldProcessId));
            validateBpmnXml(request.getBpmnXml());
            WorkflowTemplateValidator.validate(request.getBpmnXml(), request.getEditorData());
        }
        copy(request, entity);
        // 保留上次Flowable定义信息，当前XML标记为待发布草稿。
        entity.setPublishStatus("DRAFT");
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
        List<Long> matchingSourceTemplateIds = Collections.emptyList();
        if (!value.isEmpty()) {
            matchingSourceTemplateIds = mapper.selectList(Wrappers.<WorkflowTemplateEntity>lambdaQuery()
                            .like(WorkflowTemplateEntity::getTemplateName, value))
                    .stream().map(WorkflowTemplateEntity::getId).collect(Collectors.toList());
        }

        final List<Long> sourceIdsForSearch = matchingSourceTemplateIds;
        Page<FlowTemplate> flowResult = flowTemplateMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<FlowTemplate>lambdaQuery()
                        .and(!value.isEmpty(), query -> {
                            query.like(FlowTemplate::getTemplateName, value)
                                    .or().like(FlowTemplate::getTemplateNumber, value);
                            if (!sourceIdsForSearch.isEmpty()) {
                                query.or().in(FlowTemplate::getSourceTemplateId, sourceIdsForSearch);
                            }
                        })
                        .orderByDesc(FlowTemplate::getUpdateTime)
                        .orderByDesc(FlowTemplate::getId));

        Set<Long> sourceTemplateIds = flowResult.getRecords().stream().map(FlowTemplate::getSourceTemplateId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, WorkflowTemplateEntity> templates = sourceTemplateIds.isEmpty()
                ? Collections.emptyMap()
                : mapper.selectBatchIds(sourceTemplateIds).stream()
                .collect(Collectors.toMap(WorkflowTemplateEntity::getId, item -> item));

        List<WorkflowTemplateResponses.FlowPageItem> records = flowResult.getRecords().stream()
                .map(flowTemplate -> flowPageItem(flowTemplate,
                        templates.get(flowTemplate.getSourceTemplateId())))
                .collect(Collectors.toList());
        return new WorkflowTemplateResponses.FlowPage(
                flowResult.getTotal(), flowResult.getCurrent(), flowResult.getSize(), records);
    }

    @Transactional
    public void delete(Long id) { require(id); mapper.deleteById(id); }

    @Transactional
    public WorkflowResponses.Definition deploy(Long id) {
        WorkflowTemplateEntity entity = require(id);
        validateBpmnXml(entity.getBpmnXml());
        WorkflowTemplateValidator.validate(entity.getBpmnXml(), readJson(entity.getEditorData()));
        WorkflowRequests.DeployDefinition request = new WorkflowRequests.DeployDefinition();
        request.setName(entity.getTemplateName());
        request.setCategory("AGV_TEMPLATE");
        request.setResourceName(entity.getTemplateNumber() + ".bpmn20.xml");
        request.setBpmnXml(entity.getBpmnXml());
        WorkflowResponses.Definition definition = workflowService.deploy(request);
        entity.setDeploymentId(definition.getDeploymentId());
        entity.setProcessDefinitionId(definition.getId());
        entity.setDeployedVersion(definition.getVersion());
        entity.setPublishStatus("PUBLISHED");
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
        entity.setBpmnXml(writeNodePropertiesToBpmn(request.getBpmnXml(), request.getEditorData()));
        entity.setEditorData(writeJson(request.getEditorData()));
    }

    /**
     * 将页面节点属性同步写入BPMN XML。订单详情优先读取XML，editorData仅用于兼容旧模板。
     */
    private String writeNodePropertiesToBpmn(String bpmnXml, JsonNode editorData) {
        JsonNode properties = editorData == null ? null : editorData.path("nodeProperties");
        if (properties == null || !properties.isObject()) return bpmnXml;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));

            NodeList elements = document.getElementsByTagName("*");
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                String nodeId = StringUtils.trimToNull(element.getAttribute("id"));
                if (nodeId == null) continue;
                JsonNode nodeProperty = properties.get(nodeId);
                if (nodeProperty == null || !nodeProperty.isObject()) continue;
                writeFlowableAttribute(element, "completionCriteria", jsonText(nodeProperty, "completionCriteria"));
                writeFlowableAttribute(element, "failureStrategy", jsonText(nodeProperty, "failureStrategy"));
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception exception) {
            throw new IllegalArgumentException("BPMN节点属性写入XML失败", exception);
        }
    }

    private void writeFlowableAttribute(Element element, String name, String value) {
        String namespace = "http://flowable.org/bpmn";
        if (StringUtils.isBlank(value)) {
            element.removeAttributeNS(namespace, name);
        } else {
            element.setAttributeNS(namespace, "flowable:" + name, value);
        }
    }

    private String jsonText(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) return null;
        return StringUtils.trimToNull(node.path(field).asText());
    }

    private WorkflowTemplateResponses.Detail detail(WorkflowTemplateEntity value) {
        boolean deployed = "PUBLISHED".equals(value.getPublishStatus())
                || (value.getPublishStatus() == null && StringUtils.isNotBlank(value.getProcessDefinitionId()));
        return new WorkflowTemplateResponses.Detail(value.getId(), value.getTemplateNumber(), value.getTemplateName(),
                value.getApplicableObject(), value.getBpmnXml(), readJson(value.getEditorData()), value.getDeploymentId(),
                value.getProcessDefinitionId(), value.getDeployedVersion(), deployed ? "PUBLISHED" : "DRAFT",
                deployed ? "已发布" : "草稿", value.getCreatedAt(), value.getUpdatedAt());
    }

    private String validateBpmnXml(String bpmnXml) {
        String id = processId(bpmnXml);
        if (StringUtils.isBlank(id)) throw new IllegalArgumentException("BPMN XML必须包含有效的process id");
        return id;
    }

    private String processId(String bpmnXml) {
        if (StringUtils.isBlank(bpmnXml)) throw new IllegalArgumentException("BPMN XML不能为空");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
            NodeList processes = document.getElementsByTagNameNS("*", "process");
            if (processes.getLength() == 0) return null;
            return ((Element) processes.item(0)).getAttribute("id");
        } catch (Exception exception) {
            throw new IllegalArgumentException("BPMN XML格式错误", exception);
        }
    }

    /** 编辑已发布模板时自动保持原process id，确保重新发布仍生成同一流程Key的新版本。 */
    private String normalizeProcessId(String bpmnXml, String incomingId, String originalId) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
            NodeList processes = document.getElementsByTagNameNS("*", "process");
            if (processes.getLength() == 0) throw new IllegalArgumentException("BPMN XML缺少process元素");
            ((Element) processes.item(0)).setAttribute("id", originalId);

            NodeList elements = document.getElementsByTagName("*");
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                if (incomingId.equals(element.getAttribute("processRef"))) {
                    element.setAttribute("processRef", originalId);
                }
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("BPMN process id自动修正失败", exception);
        }
    }

    private WorkflowTemplateResponses.Summary summary(WorkflowTemplateEntity value) {
        return new WorkflowTemplateResponses.Summary(value.getId(), value.getTemplateNumber(), value.getTemplateName(),
                value.getApplicableObject(), value.getProcessDefinitionId(), value.getDeployedVersion(), value.getUpdatedAt());
    }

    private WorkflowTemplateResponses.PageItem pageItem(WorkflowTemplateEntity value) {
        List<String> sequence = parseMainActionSequence(value.getBpmnXml());
        boolean deployed = "PUBLISHED".equals(value.getPublishStatus())
                || (value.getPublishStatus() == null && StringUtils.isNotBlank(value.getProcessDefinitionId()));
        return new WorkflowTemplateResponses.PageItem(value.getId(), value.getTemplateNumber(), value.getTemplateName(),
                sequence, String.join(" → ", sequence), value.getApplicableObject(),
                value.getDeployedVersion(), deployed ? "ENABLED" : "DRAFT", deployed ? "已启用" : "草稿",
                value.getProcessDefinitionId(), value.getUpdatedAt());
    }

    private WorkflowTemplateResponses.FlowPageItem flowPageItem(
            FlowTemplate flowTemplate, WorkflowTemplateEntity template) {
        String displayNumber = StringUtils.defaultIfBlank(flowTemplate.getTemplateNumber(),
                "FLOW-" + flowTemplate.getId());
        String flowName = flowTemplate.getTemplateName();
        String templateName = template == null ? null : template.getTemplateName();
        Integer nodeCount = template == null ? 0 : parseMainActionSequence(template.getBpmnXml()).size();
//        String processDefinitionId = StringUtils.defaultIfBlank(flow.getProcessDefinitionId(),
//                template == null ? null : template.getProcessDefinitionId());
        return new WorkflowTemplateResponses.FlowPageItem(
                flowTemplate.getId(), displayNumber, flowName, flowTemplate.getSourceTemplateId(), templateName,
                nodeCount, flowTemplate.getUpdateTime());
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
            Map<String, String> edgeLabels = new HashMap<>();
            Set<String> gatewayIds = new HashSet<>();
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
                    String label = StringUtils.trimToNull(element.getAttribute("name"));
                    if (label != null) edgeLabels.put(
                            element.getAttribute("sourceRef") + "\u0000" + element.getAttribute("targetRef"), label);
                    continue;
                }
                String id = element.getAttribute("id");
                if (StringUtils.isBlank(id) || isNonActionElement(type)) continue;
                String name = StringUtils.defaultIfBlank(element.getAttribute("name"), id);
                nodeNames.put(id, name);
                if ("startEvent".equals(type)) starts.add(id);
                if ("exclusiveGateway".equals(type)) gatewayIds.add(id);
            }

            List<String> result = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            for (String start : starts) appendSequence(start, null, nodeNames, outgoing,
                    edgeLabels, gatewayIds, visited, result);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("模板BPMN XML无法解析", e);
        }
    }

    private void appendSequence(String nodeId, String incomingLabel, Map<String, String> nodeNames,
                                Map<String, List<String>> outgoing, Map<String, String> edgeLabels,
                                Set<String> gatewayIds, Set<String> visited, List<String> result) {
        if (!visited.add(nodeId)) return;
        String name = nodeNames.get(nodeId);
        if (name != null && !gatewayIds.contains(nodeId)) {
            result.add(StringUtils.isBlank(incomingLabel) ? name : "[" + incomingLabel + "] " + name);
        }
        for (String target : outgoing.getOrDefault(nodeId, Collections.emptyList())) {
            String label = gatewayIds.contains(nodeId) ? edgeLabels.get(nodeId + "\u0000" + target) : null;
            appendSequence(target, label, nodeNames, outgoing, edgeLabels, gatewayIds, visited, result);
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
