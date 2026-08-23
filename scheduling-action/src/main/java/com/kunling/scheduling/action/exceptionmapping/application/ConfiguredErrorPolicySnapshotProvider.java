package com.kunling.scheduling.action.exceptionmapping.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import org.springframework.stereotype.Component;

/** 从当前 ACTIVE 映射规则生成新的执行快照。 */
@Component
public class ConfiguredErrorPolicySnapshotProvider implements ErrorPolicySnapshotProvider {
    private final ActionErrorMappingRuleService ruleService;
    private final ErrorPolicySnapshotCompiler compiler;

    public ConfiguredErrorPolicySnapshotProvider(ActionErrorMappingRuleService ruleService,
                                                 ErrorPolicySnapshotCompiler compiler) {
        this.ruleService = ruleService;
        this.compiler = compiler;
    }

    @Override
    public ObjectNode compile(ActionDefinition definition) {
        return compiler.compile(definition, ruleService.activeRules());
    }
}
