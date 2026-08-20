package com.kunling.scheduling.action.definition.domain;

/** Action 配置生命周期；与某次执行的运行状态完全分离。 */
public enum ActionDefinitionStatus {
    DRAFT,
    ACTIVE,
    DISABLED
}
