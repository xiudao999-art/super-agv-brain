package com.kunling.scheduling.app.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.app.domain.ExceptionHandlingRule;
import com.kunling.scheduling.app.domain.ExceptionHandlingRuleItem;
import com.kunling.scheduling.app.domain.ExceptionRuleItemType;
import com.kunling.scheduling.app.domain.ExceptionRuleRequests;
import com.kunling.scheduling.app.domain.ExceptionRuleResponses;
import com.kunling.scheduling.app.domain.ExceptionRuleStatus;
import com.kunling.scheduling.app.mapper.ExceptionHandlingRuleItemMapper;
import com.kunling.scheduling.app.mapper.ExceptionHandlingRuleMapper;
import com.kunling.scheduling.common.exception.ResourceNotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExceptionHandlingRuleService {
    private final ExceptionHandlingRuleMapper ruleMapper;
    private final ExceptionHandlingRuleItemMapper itemMapper;
    private final ObjectMapper objectMapper;

    public ExceptionHandlingRuleService(ExceptionHandlingRuleMapper ruleMapper,
                                        ExceptionHandlingRuleItemMapper itemMapper,
                                        ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.itemMapper = itemMapper;
        this.objectMapper = objectMapper;
    }

    public IPage<ExceptionRuleResponses.Summary> page(long pageNum, long pageSize,
                                                       ExceptionRuleStatus status, String keyword) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 200) {
            throw new IllegalArgumentException("pageNum不能小于1，pageSize范围必须为1到200");
        }
        String search = trimToNull(keyword);
        Page<ExceptionHandlingRule> result = ruleMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<ExceptionHandlingRule>lambdaQuery()
                        .eq(status != null, ExceptionHandlingRule::getStatus, status)
                        .and(search != null, query -> query
                                .like(ExceptionHandlingRule::getRuleCode, search)
                                .or().like(ExceptionHandlingRule::getRuleName, search)
                                .or().like(ExceptionHandlingRule::getExceptionCode, search)
                                .or().like(ExceptionHandlingRule::getRelatedWorkOrder, search))
                        .orderByDesc(ExceptionHandlingRule::getUpdateTime)
                        .orderByDesc(ExceptionHandlingRule::getId));
        Page<ExceptionRuleResponses.Summary> response = new Page<>(result.getCurrent(), result.getSize());
        response.setTotal(result.getTotal());
        response.setRecords(result.getRecords().stream().map(this::summary).collect(Collectors.toList()));
        return response;
    }

    public ExceptionRuleResponses.Detail detail(Long id) {
        ExceptionHandlingRule rule = required(id);
        List<ExceptionHandlingRuleItem> items = itemMapper.selectList(
                Wrappers.<ExceptionHandlingRuleItem>lambdaQuery()
                        .eq(ExceptionHandlingRuleItem::getRuleId, id)
                        .orderByAsc(ExceptionHandlingRuleItem::getItemType)
                        .orderByAsc(ExceptionHandlingRuleItem::getItemSeq));
        return detail(rule, items);
    }

    @Transactional
    public ExceptionRuleResponses.Detail create(ExceptionRuleRequests.Save request) {
        normalizeAndValidate(request);
        assertRuleCodeUnique(request.getRuleCode(), null);
        ExceptionHandlingRule rule = new ExceptionHandlingRule();
        copy(request, rule);
        rule.setStatus(ExceptionRuleStatus.ENABLED);
        try {
            ruleMapper.insert(rule);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("规程编号或异常编码已经存在", exception);
        }
        saveItems(rule.getId(), ExceptionRuleItemType.SYSTEM_ACTION, request.getSystemActions());
        saveItems(rule.getId(), ExceptionRuleItemType.RELEASE_CONDITION, request.getReleaseConditions());
        return detail(rule.getId());
    }

    @Transactional
    public ExceptionRuleResponses.Detail update(Long id, ExceptionRuleRequests.Save request) {
        ExceptionHandlingRule rule = required(id);
        normalizeAndValidate(request);
        assertRuleCodeUnique(request.getRuleCode(), id);
        copy(request, rule);
        ruleMapper.updateById(rule);
        itemMapper.delete(Wrappers.<ExceptionHandlingRuleItem>lambdaQuery()
                .eq(ExceptionHandlingRuleItem::getRuleId, id));
        saveItems(id, ExceptionRuleItemType.SYSTEM_ACTION, request.getSystemActions());
        saveItems(id, ExceptionRuleItemType.RELEASE_CONDITION, request.getReleaseConditions());
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        required(id);
        itemMapper.delete(Wrappers.<ExceptionHandlingRuleItem>lambdaQuery()
                .eq(ExceptionHandlingRuleItem::getRuleId, id));
        ruleMapper.deleteById(id);
    }

    @Transactional
    public ExceptionRuleResponses.Detail changeStatus(Long id, ExceptionRuleStatus status) {
        if (status == null) throw new IllegalArgumentException("状态不能为空");
        ExceptionHandlingRule rule = required(id);
        rule.setStatus(status);
        ruleMapper.updateById(rule);
        return detail(id);
    }

    private void copy(ExceptionRuleRequests.Save request, ExceptionHandlingRule rule) {
        rule.setRuleCode(request.getRuleCode());
        rule.setRuleName(request.getRuleName());
        rule.setEmergencyScope(request.getEmergencyScope());
        rule.setResponsibility(request.getResponsibility());
        rule.setReadOnlyRule(Boolean.TRUE.equals(request.getReadOnlyRule()));
        rule.setDetectionSignal(request.getDetectionSignal());
        rule.setRelatedWorkOrder(trimToNull(request.getRelatedWorkOrder()));
        rule.setExceptionCode(request.getExceptionCode());
        rule.setManualSteps(writeJson(request.getManualSteps()));
        rule.setAutomaticExecutionNote(trimToNull(request.getAutomaticExecutionNote()));
        rule.setReleaseConditionNote(trimToNull(request.getReleaseConditionNote()));
        rule.setReleaseWarning(trimToNull(request.getReleaseWarning()));
        rule.setReleasePermission(trimToNull(request.getReleasePermission()));
        rule.setRemark(trimToNull(request.getRemark()));
    }

    private void saveItems(Long ruleId, ExceptionRuleItemType type, List<String> contents) {
        for (int index = 0; index < contents.size(); index++) {
            ExceptionHandlingRuleItem item = new ExceptionHandlingRuleItem();
            item.setRuleId(ruleId);
            item.setItemType(type);
            item.setItemSeq(index + 1);
            item.setContent(contents.get(index));
            itemMapper.insert(item);
        }
    }

    private void normalizeAndValidate(ExceptionRuleRequests.Save request) {
        request.setRuleCode(requiredText(request.getRuleCode(), "规程编号").toUpperCase());
        request.setRuleName(requiredText(request.getRuleName(), "规程名称"));
        request.setEmergencyScope(requiredText(request.getEmergencyScope(), "急停范围"));
        request.setResponsibility(requiredText(request.getResponsibility(), "处置责任"));
        request.setDetectionSignal(requiredText(request.getDetectionSignal(), "检测信号"));
        request.setExceptionCode(requiredText(request.getExceptionCode(), "异常编码").toUpperCase());
        request.setManualSteps(normalizeList(request.getManualSteps(), "人工处置步骤"));
        request.setSystemActions(normalizeList(request.getSystemActions(), "系统自动执行"));
        request.setReleaseConditions(normalizeList(request.getReleaseConditions(), "恢复放行条件"));
    }

    private List<String> normalizeList(List<String> values, String name) {
        if (values == null) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized == null) throw new IllegalArgumentException(name + "不能包含空项");
            result.add(normalized);
        }
        return result;
    }

    private String requiredText(String value, String name) {
        String result = trimToNull(value);
        if (result == null) throw new IllegalArgumentException(name + "不能为空");
        return result;
    }

    private void assertRuleCodeUnique(String ruleCode, Long excludedId) {
        Long count = ruleMapper.selectCount(Wrappers.<ExceptionHandlingRule>lambdaQuery()
                .eq(ExceptionHandlingRule::getRuleCode, ruleCode)
                .ne(excludedId != null, ExceptionHandlingRule::getId, excludedId));
        if (count != null && count > 0) throw new IllegalArgumentException("规程编号已经存在: " + ruleCode);
    }

    private ExceptionHandlingRule required(Long id) {
        ExceptionHandlingRule rule = id == null ? null : ruleMapper.selectById(id);
        if (rule == null) throw new ResourceNotFoundException("异常处置规程不存在: " + id);
        return rule;
    }

    private ExceptionRuleResponses.Summary summary(ExceptionHandlingRule rule) {
        return new ExceptionRuleResponses.Summary(rule.getId(), rule.getRuleCode(), rule.getRuleName(),
                rule.getDetectionSignal(), rule.getEmergencyScope(), rule.getResponsibility(),
                rule.getRelatedWorkOrder(), rule.getStatus());
    }

    private ExceptionRuleResponses.Detail detail(ExceptionHandlingRule rule,
                                                   List<ExceptionHandlingRuleItem> items) {
        List<String> systemActions = contents(items, ExceptionRuleItemType.SYSTEM_ACTION);
        List<String> releaseConditions = contents(items, ExceptionRuleItemType.RELEASE_CONDITION);
        return new ExceptionRuleResponses.Detail(rule.getId(), rule.getRuleCode(), rule.getRuleName(),
                rule.getEmergencyScope(), rule.getResponsibility(), rule.getReadOnlyRule(),
                rule.getDetectionSignal(), rule.getRelatedWorkOrder(), rule.getExceptionCode(),
                systemActions, readJsonList(rule.getManualSteps()), releaseConditions,
                rule.getAutomaticExecutionNote(), rule.getReleaseConditionNote(), rule.getReleaseWarning(),
                rule.getReleasePermission(), rule.getStatus(), label(rule.getStatus()), rule.getRemark(),
                rule.getCreateTime(), rule.getUpdateTime());
    }

    private List<String> contents(List<ExceptionHandlingRuleItem> items, ExceptionRuleItemType type) {
        return items.stream().filter(item -> item.getItemType() == type)
                .map(ExceptionHandlingRuleItem::getContent).collect(Collectors.toList());
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? Collections.emptyList() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("人工处置步骤JSON转换失败", exception);
        }
    }

    private List<String> readJsonList(String json) {
        if (trimToNull(json) == null) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("人工处置步骤JSON解析失败", exception);
        }
    }

    private String label(ExceptionRuleStatus status) {
        return status == null ? null : status.getLabel();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
