package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CapabilityStepDefinition.class, name = "CAPABILITY"),
        @JsonSubTypes.Type(value = ActionReferenceStepDefinition.class, name = "ACTION_REF"),
        @JsonSubTypes.Type(value = ConditionStepDefinition.class, name = "CONDITION"),
        @JsonSubTypes.Type(value = ForEachStepDefinition.class, name = "FOREACH")
})
public sealed interface ActionStepDefinition permits CapabilityStepDefinition,
        ActionReferenceStepDefinition, ConditionStepDefinition, ForEachStepDefinition {

    String stepId();

    String displayName();

    String description();

    Boolean enabled();

    Integer timeoutMs();

    FailurePolicy onFailure();

    boolean gate();

    Map<String, String> outputs();

    default boolean isEnabled() {
        return enabled() == null || enabled();
    }
}
