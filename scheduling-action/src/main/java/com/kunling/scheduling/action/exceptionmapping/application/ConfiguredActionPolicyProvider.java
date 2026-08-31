package com.kunling.scheduling.action.exceptionmapping.application;

import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import org.springframework.stereotype.Component;

/** 使用固定客户端技术码目录和当前已启用厂家映射编译下游执行策略。 */
@Component
public class ConfiguredActionPolicyProvider implements ActionPolicyProvider {
    private final ActionErrorMappingRuleService ruleService;
    private final ActionPolicyCompiler compiler;

    public ConfiguredActionPolicyProvider(ActionErrorMappingRuleService ruleService,
                                          ActionPolicyCompiler compiler) {
        this.ruleService = ruleService;
        this.compiler = compiler;
    }

    @Override
    public ActionPolicyCompilation compile(ActionDefinition definition) {
        return compiler.compile(definition, ruleService.activeRules());
    }
}
