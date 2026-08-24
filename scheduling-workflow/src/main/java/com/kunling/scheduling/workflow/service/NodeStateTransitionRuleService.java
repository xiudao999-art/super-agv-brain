package com.kunling.scheduling.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kunling.scheduling.workflow.dto.StatusChangedDto;
import com.kunling.scheduling.workflow.entity.NodeStateTransitionRule;


import java.util.List;

public interface NodeStateTransitionRuleService extends IService<NodeStateTransitionRule> {

    List<NodeStateTransitionRule> listRules(String ruleSetCode, String currentState,
                                            String eventCode, Integer enabled);

    NodeStateTransitionRule getRule(Long id);

    NodeStateTransitionRule createRule(NodeStateTransitionRule rule);

    NodeStateTransitionRule updateRule(Long id, NodeStateTransitionRule rule);

    void deleteRule(Long id);

    void statusChanged(StatusChangedDto dto);
}
