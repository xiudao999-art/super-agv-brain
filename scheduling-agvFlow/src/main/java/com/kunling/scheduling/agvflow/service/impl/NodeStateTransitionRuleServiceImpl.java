package com.kunling.scheduling.agvflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.kunling.scheduling.agvflow.domain.dto.StatusChangedDto;
import com.kunling.scheduling.agvflow.domain.entity.NodeStateTransitionRule;
import com.kunling.scheduling.agvflow.mapper.NodeStateTransitionRuleMapper;
import com.kunling.scheduling.agvflow.service.NodeStateTransitionRuleService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class NodeStateTransitionRuleServiceImpl
    extends ServiceImpl<NodeStateTransitionRuleMapper, NodeStateTransitionRule>
    implements NodeStateTransitionRuleService {

    @Override
    public List<NodeStateTransitionRule> listRules(String ruleSetCode, String currentState,
                                                   String eventCode, Integer enabled) {
        return list(Wrappers.<NodeStateTransitionRule>lambdaQuery()
            .eq(ruleSetCode != null && !ruleSetCode.isEmpty(),
                NodeStateTransitionRule::getRuleSetCode, ruleSetCode)
            .eq(currentState != null && !currentState.isEmpty(),
                NodeStateTransitionRule::getCurrentState, currentState)
            .eq(eventCode != null && !eventCode.isEmpty(),
                NodeStateTransitionRule::getEventCode, eventCode)
            .eq(enabled != null, NodeStateTransitionRule::getEnabled, enabled)
            .orderByAsc(NodeStateTransitionRule::getId));
    }

    @Override
    public NodeStateTransitionRule getRule(Long id) {
        NodeStateTransitionRule rule = getById(id);
        if (rule == null) {

        }
        return rule;
    }

    @Override
    @Transactional
    public NodeStateTransitionRule createRule(NodeStateTransitionRule rule) {
        rule.setId(null);
        save(rule);
        return getRule(rule.getId());
    }

    @Override
    @Transactional
    public NodeStateTransitionRule updateRule(Long id, NodeStateTransitionRule rule) {
        getRule(id);
        rule.setId(id);
        updateById(rule);
        return getRule(id);
    }

    @Override
    @Transactional
    public void deleteRule(Long id) {
        getRule(id);
        removeById(id);
    }

    @Override
    public void statusChanged(List<StatusChangedDto> dtos) {
        dtos.forEach(dto->{
            NodeStateTransitionRule rule = getOne(Wrappers.<NodeStateTransitionRule>lambdaQuery()
                    .eq(StringUtils.isNotEmpty(dto.getNodeState()), NodeStateTransitionRule::getCurrentState, dto.getNodeState())
                    .eq(StringUtils.isNotEmpty(dto.getEventCode()), NodeStateTransitionRule::getEventCode, dto.getEventCode()));

            log.info("当前节点状态为{}",dto.getNodeState());
            log.info("外部机器人回调状态为{}",dto.getEventCode());
            log.info("机器人的状态扭转为{},是否为最终状态{},节点结束状态{}",rule.getNextState(),rule.getTerminalFlag(),rule.getTerminalResult());
            log.info("-----------------当前外部调用结束------------------------");
        });




    }
}
