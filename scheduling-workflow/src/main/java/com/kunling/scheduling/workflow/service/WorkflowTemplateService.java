package com.kunling.scheduling.workflow.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.workflow.dto.WorkflowRequests;
import com.kunling.scheduling.workflow.dto.WorkflowResponses;
import com.kunling.scheduling.workflow.dto.WorkflowTemplateRequests;
import com.kunling.scheduling.workflow.dto.WorkflowTemplateResponses;
import com.kunling.scheduling.workflow.entity.WorkflowTemplateEntity;
import com.kunling.scheduling.workflow.mapper.WorkflowTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class WorkflowTemplateService {
    private final WorkflowTemplateMapper mapper;
    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper;

    public WorkflowTemplateService(WorkflowTemplateMapper mapper, WorkflowService workflowService, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
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
