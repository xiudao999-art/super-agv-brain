package com.kunling.scheduling.action.definition.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.kunling.scheduling.action.compilation.domain.ExecutionPlan;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class PublishedAction {
    ActionDefinition definition;
    ExecutionPlan plan;
    String planHash;
    @ConstructorProperties({"definition", "plan", "planHash"})
    public PublishedAction(
            ActionDefinition definition,
            ExecutionPlan plan,
            String planHash
    ) {
        this.definition = definition;
        this.plan = plan;
        this.planHash = planHash;
    }

}
