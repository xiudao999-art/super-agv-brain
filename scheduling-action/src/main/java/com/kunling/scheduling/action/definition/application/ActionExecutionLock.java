package com.kunling.scheduling.action.definition.application;

import java.util.Optional;

/**
 * 配置写入与运行态之间的最小 seam。
 *
 * <p>定义写入只需要知道是否正在被执行，不依赖执行状态机的内部结构。</p>
 */
public interface ActionExecutionLock {

    Optional<String> findActiveExecutionIdByActionDefinitionId(String actionDefinitionId);
}
