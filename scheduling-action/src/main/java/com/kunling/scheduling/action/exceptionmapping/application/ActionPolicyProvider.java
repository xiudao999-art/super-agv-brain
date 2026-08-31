package com.kunling.scheduling.action.exceptionmapping.application;

import com.kunling.scheduling.action.definition.domain.ActionDefinition;

/** Action 组包时编译当前定义及有效异常映射的扩展接口。 */
@FunctionalInterface
public interface ActionPolicyProvider {
    ActionPolicyCompilation compile(ActionDefinition definition);
}
