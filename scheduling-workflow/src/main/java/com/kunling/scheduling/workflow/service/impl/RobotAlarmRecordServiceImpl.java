package com.kunling.scheduling.workflow.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunling.scheduling.workflow.entity.RobotAlarmRecord;
import com.kunling.scheduling.workflow.mapper.RobotAlarmRecordMapper;
import com.kunling.scheduling.workflow.resp.RobotAlarmRecordResp;
import com.kunling.scheduling.workflow.resp.RobotAlarmRuleItemResp;
import com.kunling.scheduling.workflow.service.RobotAlarmRecordService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RobotAlarmRecordServiceImpl
        extends ServiceImpl<RobotAlarmRecordMapper, RobotAlarmRecord>
        implements RobotAlarmRecordService {

    private final ObjectMapper objectMapper;

    public RobotAlarmRecordServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public IPage<RobotAlarmRecordResp> pageResp(long pageNum, long pageSize, String alarmNo,
                                               String alarmCategoryCode, Integer handlingStatus, Long nodeId) {
        IPage<RobotAlarmRecordResp> result = baseMapper.selectRespPage(new Page<>(pageNum, pageSize), trimToNull(alarmNo),
                trimToNull(alarmCategoryCode), handlingStatus, nodeId);
        fillRuleDetails(result.getRecords());
        return result;
    }

    private void fillRuleDetails(List<RobotAlarmRecordResp> records) {
        List<Long> ruleIds = records.stream().map(RobotAlarmRecordResp::getHandlingRuleId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (ruleIds.isEmpty()) {
            records.forEach(this::fillEmptyRuleDetails);
            return;
        }
        Map<Long, List<RobotAlarmRuleItemResp>> itemsByRuleId = baseMapper.selectRuleItemsByRuleIds(ruleIds)
                .stream().collect(Collectors.groupingBy(RobotAlarmRuleItemResp::getRuleId));
        records.forEach(record -> fillRuleDetails(record, itemsByRuleId.get(record.getHandlingRuleId())));
    }

    private void fillRuleDetails(RobotAlarmRecordResp record, List<RobotAlarmRuleItemResp> items) {
        List<RobotAlarmRuleItemResp> ruleItems = items == null ? Collections.emptyList() : items;
        record.setSystemProtection(contents(ruleItems, "SYSTEM_ACTION"));
        record.setReleaseConditions(contents(ruleItems, "RELEASE_CONDITION"));
        record.setManualSteps(readJsonList(record.getManualStepsJson()));
    }

    private void fillEmptyRuleDetails(RobotAlarmRecordResp record) {
        record.setSystemProtection(Collections.emptyList());
        record.setReleaseConditions(Collections.emptyList());
        record.setManualSteps(Collections.emptyList());
    }

    private List<String> contents(List<RobotAlarmRuleItemResp> items, String itemType) {
        return items.stream().filter(item -> itemType.equals(item.getItemType()))
                .map(RobotAlarmRuleItemResp::getContent).collect(Collectors.toList());
    }

    private List<String> readJsonList(String json) {
        if (trimToNull(json) == null) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("处理规则人工确认项 JSON 解析失败", exception);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
