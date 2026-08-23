package com.kunling.scheduling.action.exceptionmapping.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;

/** Action 组包依赖的异常策略快照 seam。 */
@FunctionalInterface
public interface ErrorPolicySnapshotProvider {
    ObjectNode compile(ActionDefinition definition);
}
