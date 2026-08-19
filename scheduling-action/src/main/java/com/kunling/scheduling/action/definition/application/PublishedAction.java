package com.kunling.scheduling.action.definition.application;

import com.kunling.scheduling.action.compilation.domain.ExecutionPlan;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;

public record PublishedAction(ActionDefinition definition, ExecutionPlan plan, String planHash) {
}
